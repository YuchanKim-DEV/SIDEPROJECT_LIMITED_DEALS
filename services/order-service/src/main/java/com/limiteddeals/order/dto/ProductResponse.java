package com.limiteddeals.order.dto;

import com.limiteddeals.order.domain.Product;

/** 응답용 상자. 엔티티를 그대로 내보내지 않는다. */
public record ProductResponse(Long id, String name, long price, int stock) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getPrice(), product.getStock());
    }
}
