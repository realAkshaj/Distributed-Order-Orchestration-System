package com.orderorchestration.common.messaging;

import java.time.Instant;
import java.util.Map;

public record SagaEvent(
        String sagaId,
        EventType type,
        boolean success,
        Map<String, Object> payload,
        String failureReason,
        Instant occurredAt) {

    public static SagaEvent success(String sagaId, EventType type, Map<String, Object> payload) {
        return new SagaEvent(sagaId, type, true, payload, null, Instant.now());
    }

    public static SagaEvent failure(String sagaId, EventType type, String reason) {
        return new SagaEvent(sagaId, type, false, Map.of(), reason, Instant.now());
    }
}
