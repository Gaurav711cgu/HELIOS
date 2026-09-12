package com.helios.orchestrator.core;

import com.helios.sdk.model.StepStatus;
import com.helios.sdk.model.TaskResult;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

public final class StepExecutionRecord implements Serializable {
    private final String workflowId;
    private final String stepName;
    private final int attemptNumber;
    private final String idempotencyKey;
    private final Instant createdAt;
    private volatile StepStatus status;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile String errorMessage;
    private volatile Map<String, Object> output = Map.of();

    public StepExecutionRecord(String workflowId, String stepName, int attemptNumber, String idempotencyKey, StepStatus status) {
        this.workflowId = workflowId;
        this.stepName = stepName;
        this.attemptNumber = attemptNumber;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public String workflowId() {
        return workflowId;
    }

    public String stepName() {
        return stepName;
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public StepStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public Map<String, Object> output() {
        return output;
    }

    public void markRunning() {
        this.status = StepStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void markSucceeded(TaskResult result) {
        this.status = StepStatus.SUCCEEDED;
        this.completedAt = result.completedAt();
        this.output = result.output();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = StepStatus.FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = errorMessage;
    }

    public void markCompensating() {
        this.status = StepStatus.COMPENSATING;
    }

    public void markCompensated() {
        this.status = StepStatus.COMPENSATED;
        this.completedAt = Instant.now();
    }
}
