package com.limiteddeals.order.service;

/** 재고 차감 방식. 1단계에서 방식별로 동시성 결과를 비교하려고 둔다. */
public enum StockStrategy {
    /** 읽고, 빼고, 저장. 동시에 오면 갱신 손실이 생긴다 (1-4). */
    NAIVE,
    /** UPDATE 한 문장으로 DB가 직접 뺀다 (1-5). 기본값. */
    ATOMIC,
    /** SELECT ... FOR UPDATE로 잠그고 읽는다. 다른 사람은 기다린다 (1-6). */
    PESSIMISTIC,
    /** 잠그지 않고 버전 번호로 충돌을 감지해서, 충돌하면 다시 시도한다 (1-7). */
    OPTIMISTIC
}
