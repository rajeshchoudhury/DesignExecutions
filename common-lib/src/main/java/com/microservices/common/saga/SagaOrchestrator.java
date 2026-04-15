package com.microservices.common.saga;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class SagaOrchestrator {

    public enum SagaExecutionState {
        STARTED,
        EXECUTING,
        COMPENSATING,
        COMPLETED,
        FAILED
    }

    private final List<SagaStep> steps = new ArrayList<>();
    private SagaExecutionState state = SagaExecutionState.STARTED;

    protected void addStep(SagaStep step) {
        steps.add(step);
    }

    protected List<SagaStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public SagaExecutionState getState() {
        return state;
    }

    public CompletableFuture<Void> execute() {
        String sagaId = UUID.randomUUID().toString();
        log.info("Saga [{}] started with {} steps", sagaId, steps.size());
        state = SagaExecutionState.EXECUTING;

        List<SagaStep> completedSteps = new ArrayList<>();
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);

        for (SagaStep step : steps) {
            future = future.thenCompose(v -> {
                log.info("Saga [{}] executing step: {}", sagaId, step.getStepName());
                return step.execute().thenRun(() -> {
                    completedSteps.add(step);
                    log.info("Saga [{}] step completed: {}", sagaId, step.getStepName());
                });
            });
        }

        return future
                .thenRun(() -> {
                    state = SagaExecutionState.COMPLETED;
                    log.info("Saga [{}] completed successfully", sagaId);
                })
                .exceptionally(ex -> {
                    log.error("Saga [{}] failed, starting compensation", sagaId, ex);
                    state = SagaExecutionState.COMPENSATING;
                    compensate(sagaId, completedSteps);
                    return null;
                });
    }

    private void compensate(String sagaId, List<SagaStep> completedSteps) {
        List<SagaStep> reversed = new ArrayList<>(completedSteps);
        Collections.reverse(reversed);

        CompletableFuture<Void> compensation = CompletableFuture.completedFuture(null);
        for (SagaStep step : reversed) {
            compensation = compensation.thenCompose(v -> {
                log.info("Saga [{}] compensating step: {}", sagaId, step.getStepName());
                return step.compensate()
                        .thenRun(() -> log.info("Saga [{}] step compensated: {}", sagaId, step.getStepName()))
                        .exceptionally(compEx -> {
                            log.error("Saga [{}] compensation failed for step: {}", sagaId, step.getStepName(), compEx);
                            return null;
                        });
            });
        }

        compensation.thenRun(() -> {
            state = SagaExecutionState.FAILED;
            log.info("Saga [{}] compensation complete", sagaId);
        });
    }
}
