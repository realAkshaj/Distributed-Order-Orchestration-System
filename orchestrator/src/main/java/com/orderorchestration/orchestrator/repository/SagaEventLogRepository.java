package com.orderorchestration.orchestrator.repository;

import com.orderorchestration.orchestrator.model.SagaEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SagaEventLogRepository extends JpaRepository<SagaEventLog, UUID> {

    List<SagaEventLog> findBySagaIdOrderByOccurredAt(UUID sagaId);
}
