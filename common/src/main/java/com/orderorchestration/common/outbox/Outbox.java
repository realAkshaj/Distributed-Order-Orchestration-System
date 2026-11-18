package com.orderorchestration.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Outbox {

    private final OutboxRepository repository;
    private final ObjectMapper mapper;

    public Outbox(OutboxRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void send(String topic, String key, Object message) {
        try {
            repository.save(new OutboxEvent(topic, key, mapper.writeValueAsString(message), TraceContext.current()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize " + message.getClass().getSimpleName(), e);
        }
    }
}
