package com.limiteddeals.queue.service;

import org.springframework.stereotype.Component;

/** 장애 실험용 설정값(메모리). 기본은 0이라 아무 영향이 없다. */
@Component
public class ChaosSettings {

    private volatile long validateDelayMs;

    public long getValidateDelayMs() {
        return validateDelayMs;
    }

    public void setValidateDelayMs(long validateDelayMs) {
        this.validateDelayMs = Math.max(0, validateDelayMs);
    }

    public void applyValidateDelay() {
        if (validateDelayMs > 0) {
            try {
                Thread.sleep(validateDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
