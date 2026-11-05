package com.limiteddeals.queue.controller;

import com.limiteddeals.queue.domain.QueueStats;
import com.limiteddeals.queue.domain.QueueStatus;
import com.limiteddeals.queue.service.ChaosSettings;
import com.limiteddeals.queue.service.QueueService;
import com.limiteddeals.queue.service.QueueStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
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
    private final ChaosSettings chaos;

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

    /**
     * 내 순번을 1초마다 밀어주는 스트림(SSE). 브라우저가 매번 묻지 않아도 된다.
     * 게이트웨이가 토큰(?access_token=)을 확인하고 X-User-Id를 붙여 준다. 그 값이 있으면 쿼리의 userId보다 먼저 쓴다
     * (쿼리는 손님이 마음대로 바꿀 수 있다). 쿼리 userId는 게이트웨이 인증을 끈 예전 실험용이다.
     */
    @GetMapping(path = "/stream", produces = "text/event-stream")
    public SseEmitter stream(@PathVariable long productId,
                             @RequestHeader(name = "X-User-Id", required = false) String trustedUserId,
                             @RequestParam(required = false) String userId) {
        String who = trustedUserId != null ? trustedUserId : userId;
        if (who == null || who.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 알 수 없습니다");
        }
        return streamService.subscribe(productId, who);
    }

    /** 주문 서버용 입장권 확인. 유효하면 200, 아니면 403. */
    @GetMapping("/tokens/validate")
    public ResponseEntity<Map<String, Boolean>> validate(@PathVariable long productId,
                                                         @RequestParam String userId, @RequestParam String token) {
        chaos.applyValidateDelay(); // 장애 실험: 일부러 느리게(기본 0)
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
