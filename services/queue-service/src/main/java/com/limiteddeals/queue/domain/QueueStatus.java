package com.limiteddeals.queue.domain;

/**
 * 한 사용자의 대기 상태.
 * WAITING이면 position(내 앞에 있는 사람 수 + 1)과 waiting(전체 대기 인원)을,
 * ADMITTED이면 token(입장권)과 tokenTtlSeconds(남은 시간)를 채운다.
 */
public record QueueStatus(State state, Long position, Long waiting, String token, Long tokenTtlSeconds) {

    public enum State { WAITING, ADMITTED, NOT_IN_QUEUE }

    public static QueueStatus waiting(long position, long waiting) {
        return new QueueStatus(State.WAITING, position, waiting, null, null);
    }

    public static QueueStatus admitted(String token, long ttlSeconds) {
        return new QueueStatus(State.ADMITTED, null, null, token, ttlSeconds);
    }

    public static QueueStatus notInQueue() {
        return new QueueStatus(State.NOT_IN_QUEUE, null, null, null, null);
    }
}
