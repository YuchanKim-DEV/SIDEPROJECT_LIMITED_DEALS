package com.limiteddeals.order.repository;

import com.limiteddeals.order.domain.Product;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * [atomic] 재고 차감을 DB에 한 번에 맡긴다(읽기, 빼기, 저장을 한 문장으로).
     * 바뀐 줄 수를 돌려준다. 1이면 성공, 0이면 재고 부족(또는 상품 없음).
     * clearAutomatically: 실행 후 JPA가 기억해 둔 옛 값(1차 캐시)을 비워서, 다시 읽을 때 최신 값을 읽게 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :quantity where p.id = :id and p.stock >= :quantity")
    int decreaseStockAtomically(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * [pessimistic] SELECT ... FOR UPDATE. 읽는 순간 줄을 잠가서, 트랜잭션이 끝날 때까지 다른 사람은 기다린다.
     * 3초 넘게 기다리면 포기한다(무한 대기 방지).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /**
     * [optimistic] 내가 읽었을 때의 버전과 지금 버전이 같을 때만 바꾼다(Compare-And-Set).
     * 그 사이 누가 먼저 바꿨으면 버전이 달라져서 0을 돌려주고, 호출한 쪽이 다시 시도한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.stock = :newStock, p.version = p.version + 1 where p.id = :id and p.version = :expectedVersion")
    int updateStockIfVersionMatches(@Param("id") Long id, @Param("newStock") int newStock,
                                    @Param("expectedVersion") long expectedVersion);

    /** 보상(Saga): 결제가 실패한 주문의 재고를 되돌린다. */
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :quantity where p.id = :id")
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    /** 품절 빠른 거절용: 재고 숫자만 잠그지 않고 읽는다. */
    @Query("select p.stock from Product p where p.id = :id")
    Optional<Integer> findStockById(@Param("id") Long id);
}
