package com.microservices.common.saga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaExecutionLog {

    public enum Status {
        EXECUTING,
        COMPLETED,
        COMPENSATING,
        COMPENSATED,
        FAILED
    }

    private String sagaId;
    private String stepName;
    private Status status;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;
}
