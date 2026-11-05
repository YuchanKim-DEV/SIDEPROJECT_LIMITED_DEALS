package com.limiteddeals.queue.service;

import com.limiteddeals.queue.domain.QueueStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 구독 관리. 1초마다 구독자마다 현재 상태를 보낸다. 입장권을 받으면 마지막으로 보내고 연결을 닫는다.
 * 연결은 서버 메모리에 있으므로, 서버가 여러 대면 사용자는 처음 연결한 서버에서 계속 받는다(상태 자체는 Redis에 있다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueStreamService {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final QueueService queueService;
    private final Map<SseEmitter, Subscription> subscriptions = new ConcurrentHashMap<>();

    private record Subscription(long productId, String userId) {
    }

    public SseEmitter subscribe(long productId, String userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        subscriptions.put(emitter, new Subscription(productId, userId));
        emitter.onCompletion(() -> subscriptions.remove(emitter));
        emitter.onTimeout(() -> subscriptions.remove(emitter));
        emitter.onError(e -> subscriptions.remove(emitter));
        push(emitter, new Subscription(productId, userId));
        return emitter;
    }

    @Scheduled(fixedRate = 1000)
    public void pushAll() {
        subscriptions.forEach(this::push);
    }

    private void push(SseEmitter emitter, Subscription s) {
        try {
            QueueStatus status = queueService.status(s.productId(), s.userId());
            emitter.send(SseEmitter.event().name("status").data(status));
            if (status.state() == QueueStatus.State.ADMITTED) {
                emitter.complete();
            }
        } catch (IOException | IllegalStateException disconnected) {
            subscriptions.remove(emitter);
        }
    }
}
