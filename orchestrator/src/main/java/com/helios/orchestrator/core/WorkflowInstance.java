package com.helios.orchestrator.core;

import com.helios.sdk.model.WorkflowStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

public final class WorkflowInstance implements Serializable {
    private final String id;
    private final String definitionName;
    private final String tenantId;
    private final Map<String, Object> input;
    private final Instant createdAt;
    private volatile WorkflowStatus status;
    private volatile Instant updatedAt;

    public WorkflowInstance(String id, String definitionName, String tenantId, Map<String, Object> input) {
        this.id = id;
        this.definitionName = definitionName;
        this.tenantId = tenantId;
        this.input = input == null ? Map.of() : Map.copyOf(input);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.status = WorkflowStatus.RUNNING;
    }

    public String id() {
        return id;
    }

    public String definitionName() {
        return definitionName;
    }

    public String tenantId() {
        return tenantId;
    }

    public Map<String, Object> input() {
        return input;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public WorkflowStatus status() {
        return status;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void status(WorkflowStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
