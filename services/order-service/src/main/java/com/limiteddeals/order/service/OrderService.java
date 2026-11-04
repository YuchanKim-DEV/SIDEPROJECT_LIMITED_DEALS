package com.limiteddeals.order.service;

import com.limiteddeals.order.domain.Order;
import com.limiteddeals.order.domain.Product;
import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.dto.OrderResponse;
import com.limiteddeals.order.exception.OutOfStockException;
import com.limiteddeals.order.exception.ProductNotFoundException;
import com.limiteddeals.order.repository.OrderRepository;
import com.limiteddeals.order.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 재고 차감과 주문 저장을 한 트랜잭션으로 묶는다.
     * 둘 중 하나라도 실패하면 둘 다 취소된다.
     */
    @Transactional
    public OrderResponse place(OrderCreateRequest request, StockStrategy strategy) {
        int remainingStock = switch (strategy) {
            case NAIVE -> decreaseNaively(request);
            case ATOMIC -> decreaseAtomically(request);
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

    private Product findProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
