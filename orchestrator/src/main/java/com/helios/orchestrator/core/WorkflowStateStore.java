package com.helios.orchestrator.core;

import com.helios.sdk.model.TaskResult;
import com.helios.sdk.model.WorkflowDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WorkflowStateStore {
    void registerDefinition(WorkflowDefinition definition);

    Optional<WorkflowDefinition> getDefinition(String name);

    WorkflowInstance createInstance(String definitionName, String tenantId);

    Optional<WorkflowInstance> getInstance(String workflowId);

    void markWorkflowStatus(String workflowId, com.helios.sdk.model.WorkflowStatus status);

    StepExecutionRecord markPending(String workflowId, String stepName, int attemptNumber, String idempotencyKey);

    void markRunning(String workflowId, String stepName, int attemptNumber);

    void markSucceeded(String workflowId, String stepName, int attemptNumber, TaskResult result);

    void markFailed(String workflowId, String stepName, int attemptNumber, String errorMessage);

    boolean hasSucceeded(String idempotencyKey);

    int nextAttempt(String workflowId, String stepName);

    Optional<StepExecutionRecord> latestStep(String workflowId, String stepName);

    List<StepExecutionRecord> stepsForWorkflow(String workflowId);

    List<WorkflowInstance> listTenantWorkflows(String tenantId, int offset, int limit);

    List<WorkflowInstance> runningBefore(Instant instant);
}
