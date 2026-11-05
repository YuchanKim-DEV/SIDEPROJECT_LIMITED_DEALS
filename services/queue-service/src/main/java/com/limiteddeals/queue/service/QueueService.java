package com.limiteddeals.queue.service;

import com.limiteddeals.queue.config.QueueProperties;
import com.limiteddeals.queue.domain.QueueKeys;
import com.limiteddeals.queue.domain.QueueStats;
import com.limiteddeals.queue.domain.QueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * 가상 대기열. 오픈 순간 몰린 사람들을 Redis에 줄 세우고, 1초마다 정해진 인원만 입장권을 준다.
 * 주문 서버(order-service)는 입장권이 있는 사람만 받으므로, 몰린 트래픽이 주문 서버까지 한꺼번에 가지 않는다.
 */
@Service
@RequiredArgsConstructor
public class QueueService {

    private final StringRedisTemplate redis;
    private final QueueProperties properties;

    /** 줄 서기. 이미 줄에 있으면 자리를 그대로 유지하고(NX), 이미 입장했으면 입장권을 돌려준다. */
    public QueueStatus enter(long productId, String userId) {
        QueueStatus admitted = admittedStatus(productId, userId);
        if (admitted != null) {
            return admitted;
        }
        Long seq = redis.opsForValue().increment(QueueKeys.seq(productId));
        redis.opsForZSet().addIfAbsent(QueueKeys.waiting(productId), userId, seq);
        redis.opsForSet().add(QueueKeys.ACTIVE, String.valueOf(productId));
        return status(productId, userId);
    }

    /** 내 상태 조회: 입장권이 있으면 ADMITTED, 줄에 있으면 내 순번, 둘 다 아니면 NOT_IN_QUEUE. */
    public QueueStatus status(long productId, String userId) {
        QueueStatus admitted = admittedStatus(productId, userId);
        if (admitted != null) {
            return admitted;
        }
        Long rank = redis.opsForZSet().rank(QueueKeys.waiting(productId), userId);
        if (rank == null) {
            return QueueStatus.notInQueue();
        }
        Long size = redis.opsForZSet().zCard(QueueKeys.waiting(productId));
        return QueueStatus.waiting(rank + 1, size == null ? 0 : size);
    }

    /**
     * 줄 맨 앞에서 count명을 꺼내 입장권을 준다. ZPOPMIN은 원자적이라 서버가 여러 대여도 같은 사람을 두 번 꺼내지 않는다.
     * 꺼낸 인원을 돌려준다.
     */
    public int admit(long productId, int count) {
        Set<TypedTuple<String>> popped = redis.opsForZSet().popMin(QueueKeys.waiting(productId), count);
        if (popped == null || popped.isEmpty()) {
            redis.opsForSet().remove(QueueKeys.ACTIVE, String.valueOf(productId)); // 줄이 비면 관리 목록에서 뺀다
            return 0;
        }
        Duration ttl = Duration.ofSeconds(properties.tokenTtlSeconds());
        pipelined(ops -> {
            for (TypedTuple<String> user : popped) {
                ops.opsForValue().set(QueueKeys.token(productId, user.getValue()), UUID.randomUUID().toString(), ttl);
            }
            ops.opsForValue().increment(QueueKeys.admittedCount(productId), popped.size());
        });
        return popped.size();
    }

    /** 주문 서버가 묻는 입장권 확인. 이 상품, 이 사용자의 입장권과 일치하고 아직 유효하면 true. */
    public boolean isValidToken(long productId, String userId, String token) {
        String saved = redis.opsForValue().get(QueueKeys.token(productId, userId));
        return saved != null && saved.equals(token);
    }

    public QueueStats stats(long productId) {
        Long waiting = redis.opsForZSet().zCard(QueueKeys.waiting(productId));
        String admitted = redis.opsForValue().get(QueueKeys.admittedCount(productId));
        return new QueueStats(productId, waiting == null ? 0 : waiting, admitted == null ? 0 : Long.parseLong(admitted));
    }

    /** 부하 실험용: 가상의 사용자 users명을 한꺼번에 줄 세운다(파이프라인으로 한 번에 보냄). */
    public long simulate(long productId, int users) {
        String prefix = "sim-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        Long last = redis.opsForValue().increment(QueueKeys.seq(productId), users);
        long first = (last == null ? users : last) - users + 1;
        pipelined(ops -> {
            for (int i = 0; i < users; i++) {
                ops.opsForZSet().add(QueueKeys.waiting(productId), prefix + i, first + i);
            }
        });
        redis.opsForSet().add(QueueKeys.ACTIVE, String.valueOf(productId));
        return stats(productId).waiting();
    }

    public Set<String> activeProductIds() {
        Set<String> ids = redis.opsForSet().members(QueueKeys.ACTIVE);
        return ids == null ? Set.of() : ids;
    }

    /** 여러 명령을 한 번에 묶어 보낸다(파이프라인). 왕복 횟수가 줄어 수천 건도 빠르다. */
    private void pipelined(java.util.function.Consumer<RedisOperations<String, String>> commands) {
        redis.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                commands.accept((RedisOperations<String, String>) operations);
                return null;
            }
        });
    }

    private QueueStatus admittedStatus(long productId, String userId) {
        String key = QueueKeys.token(productId, userId);
        String token = redis.opsForValue().get(key);
        if (token == null) {
            return null;
        }
        Long ttl = redis.getExpire(key);
        return QueueStatus.admitted(token, ttl == null ? 0 : ttl);
    }
}
