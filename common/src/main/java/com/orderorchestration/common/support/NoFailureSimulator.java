package com.orderorchestration.common.support;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("!dev")
class NoFailureSimulator implements FailureSimulator {

    @Override
    public boolean shouldFail(Map<String, Object> payload) {
        return false;
    }
}
