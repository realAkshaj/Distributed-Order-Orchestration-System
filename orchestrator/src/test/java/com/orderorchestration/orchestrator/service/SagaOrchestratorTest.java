package com.orderorchestration.orchestrator.service;

import com.orderorchestration.common.messaging.CommandType;
import com.orderorchestration.common.messaging.EventType;
import com.orderorchestration.common.messaging.SagaCommand;
import com.orderorchestration.common.messaging.SagaEvent;
import com.orderorchestration.common.outbox.Outbox;
import com.orderorchestration.orchestrator.model.SagaInstance;
import com.orderorchestration.orchestrator.model.SagaStatus;
import com.orderorchestration.orchestrator.repository.SagaEventLogRepository;
import com.orderorchestration.orchestrator.repository.SagaInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaOrchestratorTest {

    private final SagaInstanceRepository sagas = mock(SagaInstanceRepository.class);
    private final SagaEventLogRepository history = mock(SagaEventLogRepository.class);
    private final Outbox outbox = mock(Outbox.class);
    private final SagaOrchestrator orchestrator = new SagaOrchestrator(sagas, history, outbox);

    private SagaInstance saga;
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        saga = new SagaInstance();
        saga.setId(UUID.randomUUID());
        saga.setOrderId(orderId);
        saga.setPayload(Map.of(
                "customerId", UUID.randomUUID().toString(),
                "productId", UUID.randomUUID().toString(),
                "quantity", 2,
                "totalAmount", new BigDecimal("49.90"),
                "forceFailure", false));
        when(sagas.findById(saga.getId())).thenReturn(Optional.of(saga));
    }

    private void receive(EventType type, boolean success, String reason) {
        orchestrator.onEvent(new SagaEvent(saga.getId().toString(), type, success, Map.of("orderId", orderId.toString()), reason, null));
    }

    private void awaiting(SagaStatus status, CommandType step) {
        saga.setStatus(status);
        saga.setCurrentStep(step.name());
    }

    private SagaCommand lastCommand() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outbox).send(anyString(), eq(saga.getId().toString()), captor.capture());
        return (SagaCommand) captor.getValue();
    }

    @Test
    void orderCreatedTriggersPaymentCharge() {
        awaiting(SagaStatus.ORDER_PENDING, CommandType.ORDER_CREATE);
        receive(EventType.ORDER_CREATED, true, null);

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.PAYMENT_PENDING);
        assertThat(lastCommand().type()).isEqualTo(CommandType.PAYMENT_CHARGE);
        assertThat(lastCommand().payload()).containsEntry("orderId", orderId.toString());
    }

    @Test
    void happyPathEndsCompleted() {
        awaiting(SagaStatus.NOTIFICATION_PENDING, CommandType.NOTIFICATION_SEND);
        receive(EventType.NOTIFICATION_SENT, true, null);

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(saga.getCurrentStep()).isNull();
    }

    @Test
    void notificationFailureStillCompletesWithoutCompensation() {
        awaiting(SagaStatus.NOTIFICATION_PENDING, CommandType.NOTIFICATION_SEND);
        receive(EventType.NOTIFICATION_SENT, false, "smtp down");

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        verify(outbox, never()).send(anyString(), anyString(), any());
    }

    @Test
    void paymentFailureCancelsOrder() {
        awaiting(SagaStatus.PAYMENT_PENDING, CommandType.PAYMENT_CHARGE);
        receive(EventType.PAYMENT_CHARGE_FAILED, false, "declined");

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
        assertThat(saga.getFailureReason()).isEqualTo("declined");
        assertThat(lastCommand().type()).isEqualTo(CommandType.ORDER_CANCEL);
    }

    @Test
    void inventoryFailureRefundsPaymentThenCancelsOrder() {
        awaiting(SagaStatus.INVENTORY_PENDING, CommandType.INVENTORY_RESERVE);
        receive(EventType.INVENTORY_RESERVATION_FAILED, false, "out of stock");
        assertThat(saga.getCurrentStep()).isEqualTo(CommandType.PAYMENT_REFUND.name());

        receive(EventType.PAYMENT_REFUNDED, true, null);
        assertThat(saga.getCurrentStep()).isEqualTo(CommandType.ORDER_CANCEL.name());

        receive(EventType.ORDER_CANCELLED, true, null);
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(saga.getFailureReason()).isEqualTo("out of stock");
    }

    @Test
    void duplicateEventIsIgnored() {
        awaiting(SagaStatus.INVENTORY_PENDING, CommandType.INVENTORY_RESERVE);
        receive(EventType.PAYMENT_CHARGED, true, null);

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.INVENTORY_PENDING);
        verify(outbox, never()).send(anyString(), anyString(), any());
    }

    @Test
    void eventForFinishedSagaIsIgnored() {
        saga.setStatus(SagaStatus.COMPLETED);
        saga.setCurrentStep(null);
        receive(EventType.NOTIFICATION_SENT, true, null);

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        verify(outbox, never()).send(anyString(), anyString(), any());
    }
}
