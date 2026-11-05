package com.limiteddeals.order.service;

import com.limiteddeals.order.domain.IdempotencyRecord;
import com.limiteddeals.order.domain.Order;
import com.limiteddeals.order.domain.Product;
import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.dto.OrderResponse;
import com.limiteddeals.order.exception.IdempotencyInProgressException;
import com.limiteddeals.order.exception.IdempotencyKeyMismatchException;
import com.limiteddeals.order.exception.OptimisticConflictException;
import com.limiteddeals.order.exception.OutOfStockException;
import com.limiteddeals.order.exception.ProductNotFoundException;
import com.limiteddeals.order.repository.IdempotencyRecordRepository;
import com.limiteddeals.order.repository.OrderRepository;
import com.limiteddeals.order.outbox.OrderPlacedEvent;
import com.limiteddeals.order.outbox.OutboxEvent;
import com.limiteddeals.order.outbox.OutboxEventRepository;
import com.limiteddeals.order.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * 주문 한 번의 시도를 한 트랜잭션으로 처리한다(재고 차감 + 주문 저장).
 * 둘 중 하나라도 실패하면 둘 다 취소된다. 재시도처럼 트랜잭션 바깥의 일은 OrderFacade가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    /** 기본 방식(한 줄 UPDATE)으로 주문한다. */
    @Transactional
    public OrderResponse place(OrderCreateRequest request) {
        return place(request, StockStrategy.ATOMIC);
    }

    @Transactional
    public OrderResponse place(OrderCreateRequest request, StockStrategy strategy) {
        return place(request, strategy, null);
    }

    /**
     * 재고 차감 + 주문 저장 + Outbox 이벤트 저장을 한 트랜잭션으로 처리한다.
     * 셋 중 하나라도 실패하면 모두 취소되므로, "주문은 있는데 이벤트가 없는" 상태가 생기지 않는다.
     */
    @Transactional
    public OrderResponse place(OrderCreateRequest request, StockStrategy strategy, String userId) {
        int remainingStock = switch (strategy) {
            case NAIVE -> decreaseNaively(request);
            case ATOMIC -> decreaseAtomically(request);
            case PESSIMISTIC -> decreasePessimistically(request);
            case OPTIMISTIC -> decreaseOptimistically(request);
        };
        Order order = orderRepository.save(new Order(request.productId(), request.quantity(), userId));
        saveOrderPlacedEvent(order);
        return new OrderResponse(order.getId(), request.productId(), order.getQuantity(), remainingStock);
    }

    private void saveOrderPlacedEvent(Order order) {
        String eventId = UUID.randomUUID().toString();
        OrderPlacedEvent event = new OrderPlacedEvent(eventId, order.getId(), order.getProductId(), order.getQuantity(),
                order.getUserId(), Instant.now());
        outboxEventRepository.save(new OutboxEvent(eventId, OrderPlacedEvent.TYPE, String.valueOf(order.getProductId()),
                jsonMapper.writeValueAsString(event)));
    }

    /** 1-4: 읽고, 빼고, 저장. 동시에 오면 같은 값을 읽고 덮어써서 갱신 손실이 생긴다. */
    private int decreaseNaively(OrderCreateRequest request) {
        Product product = findProduct(request.productId());
        product.decreaseStock(request.quantity());
        return product.getStock();
    }

    /** 1-5: UPDATE 한 문장. DB가 줄을 잠그고 최신 값에서 빼므로 갱신 손실이 없다. */
    private int decreaseAtomically(OrderCreateRequest request) {
        int updated = productRepository.decreaseStockAtomically(request.productId(), request.quantity());
        Product product = findProduct(request.productId()); // 없으면 404, 있으면 최신 재고
        if (updated == 0) {
            throw new OutOfStockException(product.getId(), product.getStock(), request.quantity());
        }
        return product.getStock();
    }

    /** 1-6: SELECT ... FOR UPDATE로 잠그고 읽는다. 트랜잭션이 끝날 때까지 다른 주문은 기다린다. */
    private int decreasePessimistically(OrderCreateRequest request) {
        Product product = productRepository.findByIdForUpdate(request.productId())
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));
        product.decreaseStock(request.quantity()); // 잠근 상태라 다른 사람이 끼어들 수 없다
        return product.getStock();
    }

    /** 1-7: 잠그지 않고 읽은 뒤, 버전이 그대로일 때만 바꾼다. 누가 먼저 바꿨으면 충돌로 알리고 다시 시도하게 한다. */
    private int decreaseOptimistically(OrderCreateRequest request) {
        Product product = findProduct(request.productId());
        if (product.getStock() < request.quantity()) {
            throw new OutOfStockException(product.getId(), product.getStock(), request.quantity());
        }
        int newStock = product.getStock() - request.quantity();
        int updated = productRepository.updateStockIfVersionMatches(product.getId(), newStock, product.getVersion());
        if (updated == 0) {
            throw new OptimisticConflictException(product.getId());
        }
        return newStock;
    }

    /**
     * 멱등 키와 함께 주문한다. 키를 "먼저" 저장해서 자리를 잡은 뒤 주문을 처리하고, 결과를 키 기록에 남긴다.
     * 모두 한 트랜잭션이라, 주문이 실패하면 키 기록도 함께 취소되어 같은 키로 다시 시도할 수 있다.
     * 같은 키가 이미 있으면 saveAndFlush에서 중복 오류(DataIntegrityViolationException)가 나고, OrderFacade가 처음 결과를 다시 돌려준다.
     */
    @Transactional
    public OrderResponse placeWithIdempotencyKey(String key, String requestHash, OrderCreateRequest request,
                                                 StockStrategy strategy, String userId) {
        IdempotencyRecord record = idempotencyRecordRepository.saveAndFlush(new IdempotencyRecord(key, requestHash));
        OrderResponse response = place(request, strategy, userId);
        record.complete(response);
        // atomic/optimistic 쿼리의 clearAutomatically가 1차 캐시를 비우면서 record도 관리 대상에서 빠진다(detached).
        // 그래서 변경 감지에 기대지 않고 명시적으로 저장(merge)한다.
        idempotencyRecordRepository.save(record);
        return response;
    }

    /** 이미 처리된 멱등 키의 처음 결과를 찾는다. */
    @Transactional(readOnly = true)
    public OrderResponse findReplay(String key, String requestHash) {
        IdempotencyRecord record = idempotencyRecordRepository.findByIdempotencyKey(key)
                .orElseThrow(() -> new IdempotencyInProgressException(key)); // 첫 요청이 취소됐거나 아직 안 보임
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyMismatchException(key);
        }
        if (!record.isCompleted()) {
            throw new IdempotencyInProgressException(key);
        }
        return record.toResponse();
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
