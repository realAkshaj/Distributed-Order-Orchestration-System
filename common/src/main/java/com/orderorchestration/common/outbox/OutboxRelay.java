package com.orderorchestration.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 500;

    private final OutboxRepository repository;
    private final KafkaSender sender;
    private final TransactionTemplate tx;

    public OutboxRelay(OutboxRepository repository, KafkaSender sender, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.sender = sender;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${outbox.relay-delay-ms:100}")
    public void relay() {
        boolean more = true;
        while (more) {
            more = Boolean.TRUE.equals(tx.execute(status -> relayBatch()));
        }
    }

    private boolean relayBatch() {
        List<OutboxEvent> batch = repository.lockUnpublished(BATCH);
        if (batch.isEmpty()) {
            return false;
        }
        try {
            sender.sendAll(batch);
        } catch (Exception e) {
            log.warn("Outbox publish failed, {} rows will be retried: {}", batch.size(), e.toString());
            return false;
        }
        return batch.size() == BATCH;
    }
}
