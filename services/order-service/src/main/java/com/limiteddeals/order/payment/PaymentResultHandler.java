package com.limiteddeals.order.payment;

import com.limiteddeals.order.domain.Order;
import com.limiteddeals.order.domain.OrderStatus;
import com.limiteddeals.order.repository.OrderRepository;
import com.limiteddeals.order.repository.ProductRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제 결과를 받아 주문을 마무리한다(Saga의 마지막 단계).
 * - PaymentCompleted: PENDING_PAYMENT → PAID
 * - PaymentFailed:    PENDING_PAYMENT → CANCELLED, 잡아 둔 재고를 되돌린다(보상 트랜잭션)
 *
 * 중복 처리 방지는 Inbox 테이블 대신 상태 전이로 한다. "결제 대기일 때만 바꾼다"는 조건부 UPDATE라서
 * 같은 결과가 두 번 와도 두 번째는 바뀌는 줄이 0이고, 재고도 한 번만 되돌린다.
 * 상태 변경과 재고 복구는 한 트랜잭션이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentResultHandler {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final JsonMapper jsonMapper;
    private final MeterRegistry registry;

    /** @return 이번에 주문 상태가 바뀌었으면 true, 이미 처리된 결과면 false */
    @Transactional
    public boolean handle(String payload) {
        JsonNode event = jsonMapper.readTree(payload);
        String type = event.get("type").asString();
        long orderId = event.get("orderId").asLong();
        OrderStatus next = switch (type) {
            case "PaymentCompleted" -> OrderStatus.PAID;
            case "PaymentFailed" -> OrderStatus.CANCELLED;
            default -> null;
        };
        if (next == null) {
            return false; // 모르는 이벤트는 무시한다(나중에 이벤트 종류가 늘어도 깨지지 않게)
        }
        if (orderRepository.moveFromPendingPayment(orderId, next) == 0) {
            registry.counter("order.payment.result", "result", "duplicate").increment();
            log.info("이미 처리된 결제 결과 건너뜀: orderId={}, type={}", orderId, type);
            return false;
        }
        if (next == OrderStatus.CANCELLED) {
            Order order = orderRepository.findById(orderId).orElseThrow();
            productRepository.increaseStock(order.getProductId(), order.getQuantity());
            log.info("결제 실패로 주문 취소, 재고 {}개 되돌림: orderId={}", order.getQuantity(), orderId);
        }
        registry.counter("order.payment.result", "result", next.name()).increment();
        return true;
    }
}
