package com.orderorchestration.common.messaging;

import static com.orderorchestration.common.messaging.CommandType.*;

public enum EventType {
    ORDER_CREATED(ORDER_CREATE),
    ORDER_CREATION_FAILED(ORDER_CREATE),
    ORDER_CANCELLED(ORDER_CANCEL),
    PAYMENT_CHARGED(PAYMENT_CHARGE),
    PAYMENT_CHARGE_FAILED(PAYMENT_CHARGE),
    PAYMENT_REFUNDED(PAYMENT_REFUND),
    INVENTORY_RESERVED(INVENTORY_RESERVE),
    INVENTORY_RESERVATION_FAILED(INVENTORY_RESERVE),
    INVENTORY_RELEASED(INVENTORY_RELEASE),
    NOTIFICATION_SENT(NOTIFICATION_SEND);

    private final CommandType replyTo;

    EventType(CommandType replyTo) {
        this.replyTo = replyTo;
    }

    public CommandType replyTo() {
        return replyTo;
    }
}
