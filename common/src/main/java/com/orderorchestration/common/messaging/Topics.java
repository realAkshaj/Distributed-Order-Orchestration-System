package com.orderorchestration.common.messaging;

public final class Topics {

    public static final String SAGA_EVENTS = "saga.events";
    public static final String ORDER_COMMANDS = "saga.commands.order";
    public static final String PAYMENT_COMMANDS = "saga.commands.payment";
    public static final String INVENTORY_COMMANDS = "saga.commands.inventory";
    public static final String NOTIFICATION_COMMANDS = "saga.commands.notification";

    private Topics() {
    }
}
