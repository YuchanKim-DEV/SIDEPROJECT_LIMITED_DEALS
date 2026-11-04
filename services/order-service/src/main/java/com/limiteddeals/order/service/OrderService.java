package com.limiteddeals.order.service;

import com.limiteddeals.order.domain.Order;
import com.limiteddeals.order.domain.Product;
import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.dto.OrderResponse;
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

    /**
     * 재고 차감과 주문 저장을 한 트랜잭션으로 묶는다.
     * 둘 중 하나라도 실패하면 둘 다 취소된다.
     * (아직 동시성 대비는 없다. 1-4에서 이 코드의 문제를 직접 본다.)
     */
    @Transactional
    public OrderResponse place(OrderCreateRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));

        product.decreaseStock(request.quantity());
        Order order = orderRepository.save(new Order(product.getId(), request.quantity()));

        return new OrderResponse(order.getId(), product.getId(), order.getQuantity(), product.getStock());
    }
}
