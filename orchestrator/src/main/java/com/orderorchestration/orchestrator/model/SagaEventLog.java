package com.orderorchestration.orchestrator.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "saga_events", schema = "orchestrator")
public class SagaEventLog {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 500)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    protected SagaEventLog() {
    }

    public SagaEventLog(UUID sagaId, String eventType, boolean success, String detail) {
        this.sagaId = sagaId;
        this.eventType = eventType;
        this.success = success;
        this.detail = detail;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSagaId() {
        return sagaId;
    }

    public String getEventType() {
        return eventType;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getDetail() {
        return detail;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
