package com.orderorchestration.common.support;

import java.util.Map;

public interface FailureSimulator {

    boolean shouldFail(Map<String, Object> payload);
}
