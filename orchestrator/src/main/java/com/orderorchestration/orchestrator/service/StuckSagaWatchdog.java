package com.orderorchestration.orchestrator.service;

import com.orderorchestration.orchestrator.model.SagaInstance;
import com.orderorchestration.orchestrator.model.SagaStatus;
import com.orderorchestration.orchestrator.repository.SagaInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static com.orderorchestration.orchestrator.model.SagaStatus.*;

@Component
public class StuckSagaWatchdog {

    private static final Logger log = LoggerFactory.getLogger(StuckSagaWatchdog.class);
    private static final List<SagaStatus> IN_FLIGHT =
            List.of(ORDER_PENDING, PAYMENT_PENDING, INVENTORY_PENDING, NOTIFICATION_PENDING, COMPENSATING);

    private final SagaInstanceRepository sagas;
    private final Duration timeout;

    public StuckSagaWatchdog(SagaInstanceRepository sagas, @Value("${saga.timeout:PT5M}") Duration timeout) {
        this.sagas = sagas;
        this.timeout = timeout;
    }

    @Scheduled(fixedDelayString = "${saga.watchdog-delay-ms:30000}")
    @Transactional
    public void failStuckSagas() {
        for (SagaInstance saga : sagas.findByStatusInAndUpdatedAtBefore(IN_FLIGHT, LocalDateTime.now().minus(timeout))) {
            log.warn("Saga {} stuck in {} at {}, marking FAILED", saga.getId(), saga.getStatus(), saga.getCurrentStep());
            saga.setFailureReason("Timed out in " + saga.getStatus() + " waiting for " + saga.getCurrentStep());
            saga.setStatus(FAILED);
            saga.setCurrentStep(null);
            sagas.save(saga);
        }
    }
}
