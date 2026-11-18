package com.orderorchestration.order.service;

import com.orderorchestration.common.messaging.EventType;
import com.orderorchestration.common.messaging.SagaCommand;
import com.orderorchestration.common.messaging.SagaEvent;
import com.orderorchestration.common.messaging.Topics;
import com.orderorchestration.common.outbox.Outbox;
import com.orderorchestration.order.model.Order;
import com.orderorchestration.order.model.OrderStatus;
import com.orderorchestration.order.repository.OrderRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class OrderCommandHandler {

    private final OrderRepository orders;
    private final Outbox outbox;

    public OrderCommandHandler(OrderRepository orders, Outbox outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    @KafkaListener(topics = Topics.ORDER_COMMANDS)
    @Transactional
    public void handle(SagaCommand cmd) {
        SagaEvent event = switch (cmd.type()) {
            case ORDER_CREATE -> create(cmd);
            case ORDER_CANCEL -> cancel(cmd);
            default -> null;
        };
        if (event != null) {
            outbox.send(Topics.SAGA_EVENTS, cmd.sagaId(), event);
        }
    }

    private SagaEvent create(SagaCommand cmd) {
        Order order = orders.findBySagaId(cmd.sagaUuid()).orElse(null);
        if (order == null) {
            if (cmd.integer("quantity") < 1 || cmd.decimal("totalAmount").signum() <= 0) {
                return SagaEvent.failure(cmd.sagaId(), EventType.ORDER_CREATION_FAILED, "Invalid quantity or amount");
            }
            order = new Order();
            order.setSagaId(cmd.sagaUuid());
            order.setCustomerId(cmd.uuid("customerId"));
            order.setProductId(cmd.uuid("productId"));
            order.setQuantity(cmd.integer("quantity"));
            order.setTotalAmount(cmd.decimal("totalAmount"));
            order.setStatus(OrderStatus.CREATED);
            orders.save(order);
        }
        return SagaEvent.success(cmd.sagaId(), EventType.ORDER_CREATED, Map.of("orderId", order.getId().toString()));
    }

    private SagaEvent cancel(SagaCommand cmd) {
        orders.findBySagaId(cmd.sagaUuid()).ifPresent(order -> {
            order.setStatus(OrderStatus.CANCELLED);
            orders.save(order);
        });
        return SagaEvent.success(cmd.sagaId(), EventType.ORDER_CANCELLED, Map.of());
    }
}
