package com.orderorchestration.inventory.repository;

import com.orderorchestration.inventory.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    Optional<Reservation> findBySagaId(UUID sagaId);
}
