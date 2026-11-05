package com.limiteddeals.queue.domain;

/** 대시보드용 대기열 현황. */
public record QueueStats(long productId, long waiting, long admitted) {
}
