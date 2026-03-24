package com.orderorchestration.orchestrator.model;

public enum SagaStatus {
    STARTED,
    ORDER_PENDING,
    PAYMENT_PENDING,
    INVENTORY_PENDING,
    NOTIFICATION_PENDING,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
