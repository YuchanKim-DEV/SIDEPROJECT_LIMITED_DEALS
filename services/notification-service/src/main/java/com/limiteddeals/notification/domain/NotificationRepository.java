package com.limiteddeals.notification.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByOrderByIdDesc(Pageable pageable);

    long countByProductId(Long productId);

    @Query("select count(distinct n.orderId) from Notification n where n.productId = :productId")
    long countDistinctOrdersByProductId(Long productId);
}
