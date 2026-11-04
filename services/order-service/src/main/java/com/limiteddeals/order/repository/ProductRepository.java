package com.limiteddeals.order.repository;

import com.limiteddeals.order.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 재고 차감을 DB에 한 번에 맡긴다(읽기, 빼기, 저장을 한 문장으로).
     * 바뀐 줄 수를 돌려준다. 1이면 성공, 0이면 재고 부족(또는 상품 없음).
     * clearAutomatically: 실행 후 JPA가 기억해 둔 옛 값(1차 캐시)을 비워서, 다시 읽을 때 최신 값을 읽게 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :quantity where p.id = :id and p.stock >= :quantity")
    int decreaseStockAtomically(@Param("id") Long id, @Param("quantity") int quantity);
}
