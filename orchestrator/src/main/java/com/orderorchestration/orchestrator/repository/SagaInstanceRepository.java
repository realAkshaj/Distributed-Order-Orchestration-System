package com.orderorchestration.orchestrator.repository;

import com.orderorchestration.orchestrator.model.SagaInstance;
import com.orderorchestration.orchestrator.model.SagaStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {

    List<SagaInstance> findByStatusInAndUpdatedAtBefore(Collection<SagaStatus> statuses, LocalDateTime before);

    List<SagaInstance> findByStatusOrderByCreatedAtDesc(SagaStatus status, Pageable page);

    List<SagaInstance> findAllByOrderByCreatedAtDesc(Pageable page);

    @Query("select s.status, count(s) from SagaInstance s group by s.status")
    List<Object[]> countByStatus();

    @Query(value = """
            SELECT coalesce(avg(extract(epoch FROM (updated_at - created_at)) * 1000), 0)
            FROM saga_instances WHERE status = 'COMPLETED'
            """, nativeQuery = true)
    double averageCompletionMillis();
}
