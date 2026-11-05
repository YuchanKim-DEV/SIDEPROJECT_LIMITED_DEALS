package com.limiteddeals.queue.domain;

/**
 * Redis 키 이름을 한곳에 모은다.
 * waiting: 줄(Sorted Set). member=사용자 id, score=도착 순번. 점수가 작을수록 앞이다.
 * seq: 도착 순번을 매기는 카운터. 같은 밀리초에 와도 순서가 겹치지 않게 시간 대신 쓴다.
 * token: 입장권. TTL이 지나면 Redis가 알아서 지운다.
 */
public final class QueueKeys {

    public static final String ACTIVE = "queue:active";
    public static final String SCHEDULER_LOCK = "queue:scheduler:lock";

    private QueueKeys() {
    }

    public static String waiting(long productId) {
        return "queue:" + productId + ":waiting";
    }

    public static String seq(long productId) {
        return "queue:" + productId + ":seq";
    }

    public static String admittedCount(long productId) {
        return "queue:" + productId + ":admitted";
    }

    public static String token(long productId, String userId) {
        return "queue:" + productId + ":token:" + userId;
    }
}
