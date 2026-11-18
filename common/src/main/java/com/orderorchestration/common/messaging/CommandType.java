package com.orderorchestration.common.messaging;

public enum CommandType {
    ORDER_CREATE(Topics.ORDER_COMMANDS),
    ORDER_CANCEL(Topics.ORDER_COMMANDS),
    PAYMENT_CHARGE(Topics.PAYMENT_COMMANDS),
    PAYMENT_REFUND(Topics.PAYMENT_COMMANDS),
    INVENTORY_RESERVE(Topics.INVENTORY_COMMANDS),
    INVENTORY_RELEASE(Topics.INVENTORY_COMMANDS),
    NOTIFICATION_SEND(Topics.NOTIFICATION_COMMANDS);

    private final String topic;

    CommandType(String topic) {
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }
}
