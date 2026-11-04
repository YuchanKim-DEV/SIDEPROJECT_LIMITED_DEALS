package com.limiteddeals.order.repository;

import com.limiteddeals.order.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
