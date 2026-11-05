package com.limiteddeals.order.service;

import com.limiteddeals.order.domain.Order;
import com.limiteddeals.order.domain.Product;
import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.dto.OrderResponse;
import com.limiteddeals.order.exception.OptimisticConflictException;
import com.limiteddeals.order.exception.OutOfStockException;
import com.limiteddeals.order.exception.ProductNotFoundException;
import com.limiteddeals.order.repository.OrderRepository;
import com.limiteddeals.order.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 한 번의 시도를 한 트랜잭션으로 처리한다(재고 차감 + 주문 저장).
 * 둘 중 하나라도 실패하면 둘 다 취소된다. 재시도처럼 트랜잭션 바깥의 일은 OrderFacade가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    /** 기본 방식(한 줄 UPDATE)으로 주문한다. */
    @Transactional
    public OrderResponse place(OrderCreateRequest request) {
        return place(request, StockStrategy.ATOMIC);
    }

    @Transactional
    public OrderResponse place(OrderCreateRequest request, StockStrategy strategy) {
        int remainingStock = switch (strategy) {
            case NAIVE -> decreaseNaively(request);
            case ATOMIC -> decreaseAtomically(request);
            case PESSIMISTIC -> decreasePessimistically(request);
            case OPTIMISTIC -> decreaseOptimistically(request);
        };
        Order order = orderRepository.save(new Order(request.productId(), request.quantity()));
        return new OrderResponse(order.getId(), request.productId(), order.getQuantity(), remainingStock);
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

    private Product findProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
