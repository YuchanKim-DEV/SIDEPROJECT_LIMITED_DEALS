package com.limiteddeals.notification.controller;

import com.limiteddeals.notification.domain.InboxEventRepository;
import com.limiteddeals.notification.domain.Notification;
import com.limiteddeals.notification.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final InboxEventRepository inboxRepository;

    @GetMapping
    public List<Notification> recent(@RequestParam(defaultValue = "20") int limit) {
        return notificationRepository.findAllByOrderByIdDesc(PageRequest.of(0, Math.min(limit, 200)));
    }

    /** 실험 확인용: 이 상품의 알림 수와, 그중 서로 다른 주문 수(같으면 중복 알림이 없다는 뜻). */
    @GetMapping("/stats")
    public Map<String, Long> stats(@RequestParam Long productId) {
        return Map.of(
                "notifications", notificationRepository.countByProductId(productId),
                "distinctOrders", notificationRepository.countDistinctOrdersByProductId(productId),
                "inboxTotal", inboxRepository.count());
    }
}
