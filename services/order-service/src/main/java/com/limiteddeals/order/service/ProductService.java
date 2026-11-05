package com.limiteddeals.order.service;

import com.limiteddeals.order.domain.Product;
import com.limiteddeals.order.dto.ProductCreateRequest;
import com.limiteddeals.order.dto.ProductResponse;
import com.limiteddeals.order.exception.ProductNotFoundException;
import com.limiteddeals.order.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public ProductResponse create(ProductCreateRequest request) {
        Product saved = productRepository.save(new Product(request.name(), request.price(), request.stock()));
        return ProductResponse.from(saved);
    }

    public ProductResponse get(Long id) {
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
