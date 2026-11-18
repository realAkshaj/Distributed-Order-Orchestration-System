package com.orderorchestration.notification.service;

import com.orderorchestration.common.messaging.CommandType;
import com.orderorchestration.common.messaging.EventType;
import com.orderorchestration.common.messaging.SagaCommand;
import com.orderorchestration.common.messaging.SagaEvent;
import com.orderorchestration.common.messaging.Topics;
import com.orderorchestration.common.outbox.Outbox;
import com.orderorchestration.common.support.FailureSimulator;
import com.orderorchestration.notification.model.Notification;
import com.orderorchestration.notification.model.NotificationStatus;
import com.orderorchestration.notification.repository.NotificationRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class NotificationCommandHandler {

    private final NotificationRepository notifications;
    private final Outbox outbox;
    private final FailureSimulator simulator;

    public NotificationCommandHandler(NotificationRepository notifications, Outbox outbox, FailureSimulator simulator) {
        this.notifications = notifications;
        this.outbox = outbox;
        this.simulator = simulator;
    }

    @KafkaListener(topics = Topics.NOTIFICATION_COMMANDS)
    @Transactional
    public void handle(SagaCommand cmd) {
        if (cmd.type() != CommandType.NOTIFICATION_SEND) {
            return;
        }
        Notification notification = notifications.findByIdempotencyKey(cmd.idempotencyKey()).orElseGet(() -> {
            Notification created = new Notification();
            created.setSagaId(cmd.sagaUuid());
            created.setOrderId(cmd.uuid("orderId"));
            created.setType("ORDER_CONFIRMATION");
            created.setIdempotencyKey(cmd.idempotencyKey());
            boolean failed = simulator.shouldFail(Map.of());
            created.setStatus(failed ? NotificationStatus.FAILED : NotificationStatus.SENT);
            created.setSentAt(failed ? null : LocalDateTime.now());
            return notifications.save(created);
        });
        SagaEvent event = notification.getStatus() == NotificationStatus.SENT
                ? SagaEvent.success(cmd.sagaId(), EventType.NOTIFICATION_SENT, Map.of())
                : SagaEvent.failure(cmd.sagaId(), EventType.NOTIFICATION_SENT, "Email provider unavailable");
        outbox.send(Topics.SAGA_EVENTS, cmd.sagaId(), event);
    }
}
