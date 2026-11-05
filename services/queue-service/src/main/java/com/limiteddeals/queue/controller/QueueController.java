package com.limiteddeals.queue.controller;

import com.limiteddeals.queue.domain.QueueStats;
import com.limiteddeals.queue.domain.QueueStatus;
import com.limiteddeals.queue.service.QueueService;
import com.limiteddeals.queue.service.QueueStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 대기열 API. 사용자 구분은 X-User-Id 헤더로 한다(로그인은 9단계에서).
 */
@RestController
@RequestMapping("/api/queue/{productId}")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;
    private final QueueStreamService streamService;

    /** 줄 서기. 응답으로 내 순번(WAITING) 또는 입장권(ADMITTED)을 받는다. */
    @PostMapping("/enter")
    public QueueStatus enter(@PathVariable long productId, @RequestHeader("X-User-Id") String userId) {
        return queueService.enter(productId, userId);
    }

    /** 내 상태 조회(폴링용). */
    @GetMapping("/status")
    public QueueStatus status(@PathVariable long productId, @RequestHeader("X-User-Id") String userId) {
        return queueService.status(productId, userId);
    }

    /** 내 순번을 1초마다 밀어주는 스트림(SSE). 브라우저가 매번 묻지 않아도 된다. EventSource는 헤더를 못 보내서 userId를 쿼리로 받는다. */
    @GetMapping(path = "/stream", produces = "text/event-stream")
    public SseEmitter stream(@PathVariable long productId, @RequestParam String userId) {
        return streamService.subscribe(productId, userId);
    }

    /** 주문 서버용 입장권 확인. 유효하면 200, 아니면 403. */
    @GetMapping("/tokens/validate")
    public ResponseEntity<Map<String, Boolean>> validate(@PathVariable long productId,
                                                         @RequestParam String userId, @RequestParam String token) {
        boolean valid = queueService.isValidToken(productId, userId, token);
        return ResponseEntity.status(valid ? 200 : 403).body(Map.of("valid", valid));
    }

    /** 대시보드용 현황. */
    @GetMapping("/stats")
    public QueueStats stats(@PathVariable long productId) {
        return queueService.stats(productId);
    }

    /** 부하 실험용: 가상 사용자 users명을 한꺼번에 줄 세운다. */
    @PostMapping("/simulate")
    public QueueStats simulate(@PathVariable long productId, @RequestParam(defaultValue = "10000") int users) {
        queueService.simulate(productId, Math.min(users, 1_000_000));
        return queueService.stats(productId);
    }
}
