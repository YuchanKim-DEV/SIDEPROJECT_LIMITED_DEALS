package com.limiteddeals.order.repository;

import com.limiteddeals.order.domain.Order;
import com.limiteddeals.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 결제 대기 중인 주문만 다음 상태로 바꾼다. 바뀐 줄 수를 돌려준다(1이면 이번에 바뀜, 0이면 이미 처리됨).
     * 같은 결제 결과가 두 번 와도 두 번째는 0이 되어, 재고를 두 번 되돌리는 일이 없다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = :next where o.id = :id and o.status = com.limiteddeals.order.domain.OrderStatus.PENDING_PAYMENT")
    int moveFromPendingPayment(@Param("id") Long id, @Param("next") OrderStatus next);

    long countByProductIdAndStatus(Long productId, OrderStatus status);
}
