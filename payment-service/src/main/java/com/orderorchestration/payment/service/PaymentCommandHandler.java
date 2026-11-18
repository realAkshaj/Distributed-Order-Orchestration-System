package com.orderorchestration.payment.service;

import com.orderorchestration.common.messaging.EventType;
import com.orderorchestration.common.messaging.SagaCommand;
import com.orderorchestration.common.messaging.SagaEvent;
import com.orderorchestration.common.messaging.Topics;
import com.orderorchestration.common.outbox.Outbox;
import com.orderorchestration.common.support.FailureSimulator;
import com.orderorchestration.payment.model.Payment;
import com.orderorchestration.payment.model.PaymentStatus;
import com.orderorchestration.payment.repository.PaymentRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class PaymentCommandHandler {

    private final PaymentRepository payments;
    private final Outbox outbox;
    private final FailureSimulator simulator;

    public PaymentCommandHandler(PaymentRepository payments, Outbox outbox, FailureSimulator simulator) {
        this.payments = payments;
        this.outbox = outbox;
        this.simulator = simulator;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS)
    @Transactional
    public void handle(SagaCommand cmd) {
        SagaEvent event = switch (cmd.type()) {
            case PAYMENT_CHARGE -> charge(cmd);
            case PAYMENT_REFUND -> refund(cmd);
            default -> null;
        };
        if (event != null) {
            outbox.send(Topics.SAGA_EVENTS, cmd.sagaId(), event);
        }
    }

    private SagaEvent charge(SagaCommand cmd) {
        Payment payment = payments.findByIdempotencyKey(cmd.idempotencyKey()).orElseGet(() -> {
            Payment created = new Payment();
            created.setSagaId(cmd.sagaUuid());
            created.setOrderId(cmd.uuid("orderId"));
            created.setAmount(cmd.decimal("amount"));
            created.setIdempotencyKey(cmd.idempotencyKey());
            created.setStatus(simulator.shouldFail(cmd.payload()) ? PaymentStatus.FAILED : PaymentStatus.CHARGED);
            return payments.save(created);
        });
        return payment.getStatus() == PaymentStatus.FAILED
                ? SagaEvent.failure(cmd.sagaId(), EventType.PAYMENT_CHARGE_FAILED, "Payment declined")
                : SagaEvent.success(cmd.sagaId(), EventType.PAYMENT_CHARGED, Map.of("paymentId", payment.getId().toString()));
    }

    private SagaEvent refund(SagaCommand cmd) {
        payments.findBySagaId(cmd.sagaUuid())
                .filter(payment -> payment.getStatus() == PaymentStatus.CHARGED)
                .ifPresent(payment -> {
                    payment.setStatus(PaymentStatus.REFUNDED);
                    payments.save(payment);
                });
        return SagaEvent.success(cmd.sagaId(), EventType.PAYMENT_REFUNDED, Map.of());
    }
}
