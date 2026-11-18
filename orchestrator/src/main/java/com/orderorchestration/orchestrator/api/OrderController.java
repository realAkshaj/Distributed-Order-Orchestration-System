package com.orderorchestration.orchestrator.api;

import com.orderorchestration.orchestrator.model.SagaEventLog;
import com.orderorchestration.orchestrator.model.SagaInstance;
import com.orderorchestration.orchestrator.model.SagaStatus;
import com.orderorchestration.orchestrator.repository.SagaEventLogRepository;
import com.orderorchestration.orchestrator.repository.SagaInstanceRepository;
import com.orderorchestration.orchestrator.service.SagaOrchestrator;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class OrderController {

    private final SagaOrchestrator orchestrator;
    private final SagaInstanceRepository sagas;
    private final SagaEventLogRepository history;

    public OrderController(SagaOrchestrator orchestrator, SagaInstanceRepository sagas, SagaEventLogRepository history) {
        this.orchestrator = orchestrator;
        this.sagas = sagas;
        this.history = history;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> createOrder(
            @Valid @RequestBody OrderRequest request,
            @RequestHeader(value = "X-Force-Payment-Failure", defaultValue = "false") boolean forcePaymentFailure) {
        UUID sagaId = orchestrator.startSaga(request, forcePaymentFailure);
        return Map.of("sagaId", sagaId.toString(), "status", "ACCEPTED");
    }

    @GetMapping({"/orders/{sagaId}", "/sagas/{sagaId}"})
    public SagaInstance getSaga(@PathVariable UUID sagaId) {
        return sagas.findById(sagaId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/sagas")
    public List<SagaInstance> listSagas(
            @RequestParam(required = false) SagaStatus status,
            @RequestParam(defaultValue = "50") int limit) {
        PageRequest page = PageRequest.of(0, Math.min(limit, 200));
        return status == null ? sagas.findAllByOrderByCreatedAtDesc(page) : sagas.findByStatusOrderByCreatedAtDesc(status, page);
    }

    @GetMapping("/sagas/{sagaId}/events")
    public List<SagaEventLog> sagaEvents(@PathVariable UUID sagaId) {
        return history.findBySagaIdOrderByOccurredAt(sagaId);
    }

    @GetMapping("/sagas/stats")
    public Map<String, Object> stats() {
        Map<String, Object> counts = new HashMap<>();
        for (SagaStatus status : SagaStatus.values()) {
            counts.put(status.name(), 0L);
        }
        sagas.countByStatus().forEach(row -> counts.put(((SagaStatus) row[0]).name(), row[1]));
        return Map.of("counts", counts, "avgCompletionMillis", sagas.averageCompletionMillis());
    }
}
