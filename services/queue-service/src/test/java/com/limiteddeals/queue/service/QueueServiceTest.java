package com.limiteddeals.queue.service;

import com.limiteddeals.queue.domain.QueueStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIf("com.limiteddeals.queue.RedisAvailable#isRunning")
class QueueServiceTest {

    @Autowired QueueService queueService;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void flush() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void 먼저_온_사람이_앞_순번을_받는다() {
        queueService.enter(1, "a");
        queueService.enter(1, "b");
        QueueStatus c = queueService.enter(1, "c");

        assertThat(c.state()).isEqualTo(QueueStatus.State.WAITING);
        assertThat(c.position()).isEqualTo(3);
        assertThat(queueService.status(1, "a").position()).isEqualTo(1);
    }

    @Test
    void 다시_줄을_서도_자리가_바뀌지_않는다() {
        queueService.enter(1, "a");
        queueService.enter(1, "b");
        queueService.enter(1, "a"); // 새로고침해도 맨 뒤로 가지 않는다

        assertThat(queueService.status(1, "a").position()).isEqualTo(1);
        assertThat(queueService.stats(1).waiting()).isEqualTo(2);
    }

    @Test
    void 입장시키면_앞에서부터_정해진_인원만_입장권을_받는다() {
        for (String u : new String[]{"a", "b", "c"}) queueService.enter(1, u);

        int admitted = queueService.admit(1, 2);

        assertThat(admitted).isEqualTo(2);
        QueueStatus a = queueService.status(1, "a");
        assertThat(a.state()).isEqualTo(QueueStatus.State.ADMITTED);
        assertThat(queueService.isValidToken(1, "a", a.token())).isTrue();
        assertThat(queueService.isValidToken(1, "a", "가짜")).isFalse();
        assertThat(queueService.status(1, "c").position()).isEqualTo(1); // c가 맨 앞이 됐다
    }

    @Test
    void 동시에_1000명이_줄을_서도_순번이_겹치지_않는다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch ready = new CountDownLatch(1);
        for (int i = 0; i < 1000; i++) {
            String user = "u" + i;
            pool.submit(() -> {
                ready.await();
                return queueService.enter(1, user);
            });
        }
        ready.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(queueService.stats(1).waiting()).isEqualTo(1000);
        long distinctPositions = java.util.stream.IntStream.range(0, 1000)
                .mapToObj(i -> queueService.status(1, "u" + i).position()).distinct().count();
        assertThat(distinctPositions).isEqualTo(1000);
    }

    @Test
    void 가상_사용자를_한꺼번에_줄_세운다() {
        queueService.enter(1, "real");
        long waiting = queueService.simulate(1, 5000);

        assertThat(waiting).isEqualTo(5001);
        assertThat(queueService.status(1, "real").position()).isEqualTo(1); // 먼저 온 진짜 사용자가 앞
    }
}
