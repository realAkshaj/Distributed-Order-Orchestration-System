package com.orderorchestration.common.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Profile("dev")
class DevFailureSimulator implements FailureSimulator {

    private final double rate;

    DevFailureSimulator(@Value("${simulation.failure-rate:0.1}") double rate) {
        this.rate = rate;
    }

    @Override
    public boolean shouldFail(Map<String, Object> payload) {
        return Boolean.TRUE.equals(payload.get("forceFailure")) || ThreadLocalRandom.current().nextDouble() < rate;
    }
}
