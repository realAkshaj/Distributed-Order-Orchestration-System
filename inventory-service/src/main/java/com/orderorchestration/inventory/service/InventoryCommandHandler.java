package com.orderorchestration.inventory.service;

import com.orderorchestration.common.messaging.EventType;
import com.orderorchestration.common.messaging.SagaCommand;
import com.orderorchestration.common.messaging.SagaEvent;
import com.orderorchestration.common.messaging.Topics;
import com.orderorchestration.common.outbox.Outbox;
import com.orderorchestration.common.support.FailureSimulator;
import com.orderorchestration.inventory.model.Reservation;
import com.orderorchestration.inventory.model.ReservationStatus;
import com.orderorchestration.inventory.repository.ReservationRepository;
import com.orderorchestration.inventory.repository.StockRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class InventoryCommandHandler {

    private final ReservationRepository reservations;
    private final StockRepository stock;
    private final Outbox outbox;
    private final FailureSimulator simulator;

    public InventoryCommandHandler(ReservationRepository reservations, StockRepository stock,
                                   Outbox outbox, FailureSimulator simulator) {
        this.reservations = reservations;
        this.stock = stock;
        this.outbox = outbox;
        this.simulator = simulator;
    }

    @KafkaListener(topics = Topics.INVENTORY_COMMANDS)
    @Transactional
    public void handle(SagaCommand cmd) {
        SagaEvent event = switch (cmd.type()) {
            case INVENTORY_RESERVE -> reserve(cmd);
            case INVENTORY_RELEASE -> release(cmd);
            default -> null;
        };
        if (event != null) {
            outbox.send(Topics.SAGA_EVENTS, cmd.sagaId(), event);
        }
    }

    private SagaEvent reserve(SagaCommand cmd) {
        Reservation reservation = reservations.findByIdempotencyKey(cmd.idempotencyKey()).orElseGet(() -> {
            Reservation created = new Reservation();
            created.setSagaId(cmd.sagaUuid());
            created.setOrderId(cmd.uuid("orderId"));
            created.setProductId(cmd.uuid("productId"));
            created.setQuantity(cmd.integer("quantity"));
            created.setIdempotencyKey(cmd.idempotencyKey());
            boolean reserved = !simulator.shouldFail(cmd.payload())
                    && stock.decrementIfAvailable(created.getProductId(), created.getQuantity()) == 1;
            created.setStatus(reserved ? ReservationStatus.RESERVED : ReservationStatus.FAILED);
            return reservations.save(created);
        });
        return reservation.getStatus() == ReservationStatus.FAILED
                ? SagaEvent.failure(cmd.sagaId(), EventType.INVENTORY_RESERVATION_FAILED, "Insufficient stock")
                : SagaEvent.success(cmd.sagaId(), EventType.INVENTORY_RESERVED, Map.of("reservationId", reservation.getId().toString()));
    }

    private SagaEvent release(SagaCommand cmd) {
        reservations.findBySagaId(cmd.sagaUuid())
                .filter(reservation -> reservation.getStatus() == ReservationStatus.RESERVED)
                .ifPresent(reservation -> {
                    stock.increment(reservation.getProductId(), reservation.getQuantity());
                    reservation.setStatus(ReservationStatus.RELEASED);
                    reservations.save(reservation);
                });
        return SagaEvent.success(cmd.sagaId(), EventType.INVENTORY_RELEASED, Map.of());
    }
}
