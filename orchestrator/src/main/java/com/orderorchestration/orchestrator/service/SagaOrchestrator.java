package com.orderorchestration.orchestrator.service;

import com.orderorchestration.common.messaging.CommandType;
import com.orderorchestration.common.messaging.SagaCommand;
import com.orderorchestration.common.messaging.SagaEvent;
import com.orderorchestration.common.messaging.Topics;
import com.orderorchestration.common.outbox.Outbox;
import com.orderorchestration.orchestrator.api.OrderRequest;
import com.orderorchestration.orchestrator.model.SagaEventLog;
import com.orderorchestration.orchestrator.model.SagaInstance;
import com.orderorchestration.orchestrator.model.SagaStatus;
import com.orderorchestration.orchestrator.repository.SagaEventLogRepository;
import com.orderorchestration.orchestrator.repository.SagaInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.orderorchestration.common.messaging.CommandType.*;
import static com.orderorchestration.orchestrator.model.SagaStatus.*;

@Service
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final SagaInstanceRepository sagas;
    private final SagaEventLogRepository history;
    private final Outbox outbox;

    public SagaOrchestrator(SagaInstanceRepository sagas, SagaEventLogRepository history, Outbox outbox) {
        this.sagas = sagas;
        this.history = history;
        this.outbox = outbox;
    }

    @Transactional
    public UUID startSaga(OrderRequest request, boolean forceFailure) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("customerId", request.customerId());
        payload.put("productId", request.productId());
        payload.put("quantity", request.quantity());
        payload.put("totalAmount", request.totalAmount());
        payload.put("forceFailure", forceFailure);

        SagaInstance saga = new SagaInstance();
        saga.setPayload(payload);
        saga.setStatus(ORDER_PENDING);
        saga.setCurrentStep(ORDER_CREATE.name());
        sagas.save(saga);
        history.save(new SagaEventLog(saga.getId(), "SAGA_STARTED", true, null));
        issue(saga, ORDER_CREATE, payload);
        return saga.getId();
    }

    @KafkaListener(topics = Topics.SAGA_EVENTS)
    @Transactional
    public void onEvent(SagaEvent event) {
        UUID sagaId = UUID.fromString(event.sagaId());
        SagaInstance saga = sagas.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("Event {} for unknown saga {}", event.type(), sagaId);
            return;
        }
        if (!event.type().replyTo().name().equals(saga.getCurrentStep())) {
            log.info("Ignoring {} for saga {} (current step {})", event.type(), sagaId, saga.getCurrentStep());
            return;
        }
        history.save(new SagaEventLog(sagaId, event.type().name(), event.success(), event.failureReason()));

        switch (event.type()) {
            case ORDER_CREATED -> {
                saga.setOrderId(UUID.fromString(String.valueOf(event.payload().get("orderId"))));
                advance(saga, PAYMENT_PENDING, PAYMENT_CHARGE, paymentPayload(saga));
            }
            case ORDER_CREATION_FAILED -> terminate(saga, FAILED, event.failureReason());
            case PAYMENT_CHARGED -> advance(saga, INVENTORY_PENDING, INVENTORY_RESERVE, inventoryPayload(saga));
            case PAYMENT_CHARGE_FAILED -> {
                saga.setFailureReason(event.failureReason());
                advance(saga, COMPENSATING, ORDER_CANCEL, orderPayload(saga));
            }
            case INVENTORY_RESERVED -> advance(saga, NOTIFICATION_PENDING, NOTIFICATION_SEND, notificationPayload(saga));
            case INVENTORY_RESERVATION_FAILED -> {
                saga.setFailureReason(event.failureReason());
                advance(saga, COMPENSATING, PAYMENT_REFUND, orderPayload(saga));
            }
            case PAYMENT_REFUNDED -> advance(saga, COMPENSATING, ORDER_CANCEL, orderPayload(saga));
            case ORDER_CANCELLED -> terminate(saga, COMPENSATED, saga.getFailureReason());
            case NOTIFICATION_SENT -> terminate(saga, COMPLETED, event.success() ? null : "Notification failed: " + event.failureReason());
            case INVENTORY_RELEASED -> log.info("Inventory released for saga {}", saga.getId());
        }
    }

    private void advance(SagaInstance saga, SagaStatus status, CommandType next, Map<String, Object> payload) {
        saga.setStatus(status);
        saga.setCurrentStep(next.name());
        sagas.save(saga);
        issue(saga, next, payload);
    }

    private void terminate(SagaInstance saga, SagaStatus status, String reason) {
        saga.setStatus(status);
        saga.setCurrentStep(null);
        saga.setFailureReason(reason);
        sagas.save(saga);
    }

    private void issue(SagaInstance saga, CommandType type, Map<String, Object> payload) {
        String id = saga.getId().toString();
        outbox.send(type.topic(), id, SagaCommand.of(id, type, payload));
    }

    private Map<String, Object> orderPayload(SagaInstance saga) {
        return Map.of("orderId", saga.getOrderId().toString());
    }

    private Map<String, Object> paymentPayload(SagaInstance saga) {
        return Map.of(
                "orderId", saga.getOrderId().toString(),
                "amount", saga.getPayload().get("totalAmount"),
                "forceFailure", saga.getPayload().get("forceFailure"));
    }

    private Map<String, Object> inventoryPayload(SagaInstance saga) {
        return Map.of(
                "orderId", saga.getOrderId().toString(),
                "productId", saga.getPayload().get("productId"),
                "quantity", saga.getPayload().get("quantity"));
    }

    private Map<String, Object> notificationPayload(SagaInstance saga) {
        return Map.of(
                "orderId", saga.getOrderId().toString(),
                "customerId", saga.getPayload().get("customerId"));
    }
}
