package com.orderorchestration.common.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SagaCommand(
        String sagaId,
        CommandType type,
        String idempotencyKey,
        Map<String, Object> payload,
        Instant issuedAt) {

    public static SagaCommand of(String sagaId, CommandType type, Map<String, Object> payload) {
        return new SagaCommand(sagaId, type, sagaId + ":" + type, payload, Instant.now());
    }

    public UUID uuid(String key) {
        return UUID.fromString(String.valueOf(payload.get(key)));
    }

    public int integer(String key) {
        return new BigDecimal(String.valueOf(payload.get(key))).intValueExact();
    }

    public BigDecimal decimal(String key) {
        return new BigDecimal(String.valueOf(payload.get(key)));
    }

    public UUID sagaUuid() {
        return UUID.fromString(sagaId);
    }
}
