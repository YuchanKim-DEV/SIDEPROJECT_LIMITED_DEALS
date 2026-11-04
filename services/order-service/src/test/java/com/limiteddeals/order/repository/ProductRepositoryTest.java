package com.limiteddeals.order.repository;

import com.limiteddeals.order.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    ProductRepository productRepository;

    @Test
    void 상품을_저장하고_다시_조회한다() {
        Product saved = productRepository.save(new Product("한정판 스니커즈", 199_000, 100));

        Product found = productRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("한정판 스니커즈");
        assertThat(found.getPrice()).isEqualTo(199_000);
        assertThat(found.getStock()).isEqualTo(100);
    }
}
