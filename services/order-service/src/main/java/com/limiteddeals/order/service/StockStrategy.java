package com.limiteddeals.order.service;

/** 재고 차감 방식. 1단계에서 방식별로 동시성 결과를 비교하려고 둔다. */
public enum StockStrategy {
    /** 읽고, 빼고, 저장. 동시에 오면 갱신 손실이 생긴다 (1-4). */
    NAIVE,
    /** UPDATE 한 문장으로 DB가 직접 뺀다 (1-5). */
    ATOMIC
}
