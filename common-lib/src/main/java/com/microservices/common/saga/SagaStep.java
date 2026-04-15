package com.microservices.common.saga;

import java.util.concurrent.CompletableFuture;

public interface SagaStep {

    String getStepName();

    CompletableFuture<Void> execute();

    CompletableFuture<Void> compensate();
}
