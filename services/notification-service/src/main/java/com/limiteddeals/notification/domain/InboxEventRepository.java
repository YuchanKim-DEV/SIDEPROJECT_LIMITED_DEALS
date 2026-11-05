package com.limiteddeals.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxEventRepository extends JpaRepository<InboxEvent, Long> {

    boolean existsByEventId(String eventId);
}
