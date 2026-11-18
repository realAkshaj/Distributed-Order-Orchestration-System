package com.orderorchestration.common.outbox;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
class KafkaSender {

    private final KafkaTemplate<String, String> template;

    KafkaSender(KafkaTemplate<String, String> template) {
        this.template = template;
    }

    @CircuitBreaker(name = "kafka")
    void sendAll(List<OutboxEvent> events) throws Exception {
        List<CompletableFuture<SendResult<String, String>>> futures = new ArrayList<>(events.size());
        for (OutboxEvent event : events) {
            try (Scope ignored = TraceContext.restore(event.getTraceparent()).makeCurrent()) {
                futures.add(template.send(new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload())));
            }
        }
        Exception failure = null;
        for (int i = 0; i < events.size(); i++) {
            try {
                futures.get(i).get(10, TimeUnit.SECONDS);
                events.get(i).markPublished();
            } catch (Exception e) {
                failure = e;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
