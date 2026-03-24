package com.orderorchestration.notification.repository;

import com.orderorchestration.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);
}
