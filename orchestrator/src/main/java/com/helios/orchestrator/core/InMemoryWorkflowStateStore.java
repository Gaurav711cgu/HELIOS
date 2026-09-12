package com.helios.orchestrator.core;

import com.helios.sdk.model.StepStatus;
import com.helios.sdk.model.TaskResult;
import com.helios.sdk.model.WorkflowDefinition;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryWorkflowStateStore implements WorkflowStateStore {
    private final ConcurrentMap<String, WorkflowDefinition> definitions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WorkflowInstance> instances = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, StepExecutionRecord>> stepRecords = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> succeededIdempotencyKeys = new ConcurrentHashMap<>();

    @Override
    public void registerDefinition(WorkflowDefinition definition) {
        definitions.put(definition.name(), definition);
    }

    @Override
    public Optional<WorkflowDefinition> getDefinition(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    @Override
    public WorkflowInstance createInstance(String definitionName, String tenantId) {
        WorkflowInstance instance = new WorkflowInstance(UUID.randomUUID().toString(), definitionName, tenantId, Map.of());
        instances.put(instance.id(), instance);
        stepRecords.put(instance.id(), new ConcurrentHashMap<>());
        return instance;
    }

    @Override
    public Optional<WorkflowInstance> getInstance(String workflowId) {
        return Optional.ofNullable(instances.get(workflowId));
    }

    @Override
    public void markWorkflowStatus(String workflowId, com.helios.sdk.model.WorkflowStatus status) {
        getInstance(workflowId).ifPresent(instance -> instance.status(status));
    }

    @Override
    public StepExecutionRecord markPending(String workflowId, String stepName, int attemptNumber, String idempotencyKey) {
        StepExecutionRecord record = new StepExecutionRecord(workflowId, stepName, attemptNumber, idempotencyKey, StepStatus.PENDING);
        recordsFor(workflowId).put(recordKey(stepName, attemptNumber), record);
        return record;
    }

    @Override
    public void markRunning(String workflowId, String stepName, int attemptNumber) {
        recordsFor(workflowId).get(recordKey(stepName, attemptNumber)).markRunning();
        markWorkflowStatus(workflowId, com.helios.sdk.model.WorkflowStatus.RUNNING);
    }

    @Override
    public void markSucceeded(String workflowId, String stepName, int attemptNumber, TaskResult result) {
        StepExecutionRecord record = recordsFor(workflowId).get(recordKey(stepName, attemptNumber));
        record.markSucceeded(result);
        succeededIdempotencyKeys.put(record.idempotencyKey(), true);
    }

    @Override
    public void markFailed(String workflowId, String stepName, int attemptNumber, String errorMessage) {
        recordsFor(workflowId).get(recordKey(stepName, attemptNumber)).markFailed(errorMessage);
    }

    @Override
    public boolean hasSucceeded(String idempotencyKey) {
        return succeededIdempotencyKeys.containsKey(idempotencyKey);
    }

    @Override
    public int nextAttempt(String workflowId, String stepName) {
        return recordsFor(workflowId).values().stream()
                .filter(record -> record.stepName().equals(stepName))
                .mapToInt(StepExecutionRecord::attemptNumber)
                .max()
                .orElse(0) + 1;
    }

    @Override
    public Optional<StepExecutionRecord> latestStep(String workflowId, String stepName) {
        return recordsFor(workflowId).values().stream()
                .filter(record -> record.stepName().equals(stepName))
                .max(Comparator.comparingInt(StepExecutionRecord::attemptNumber));
    }

    @Override
    public List<StepExecutionRecord> stepsForWorkflow(String workflowId) {
        return recordsFor(workflowId).values().stream()
                .sorted(Comparator.comparing(StepExecutionRecord::stepName).thenComparingInt(StepExecutionRecord::attemptNumber))
                .toList();
    }

    @Override
    public List<WorkflowInstance> listTenantWorkflows(String tenantId, int offset, int limit) {
        return instances.values().stream()
                .filter(instance -> instance.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(WorkflowInstance::createdAt).reversed())
                .skip(Math.max(0, offset))
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public List<WorkflowInstance> runningBefore(Instant instant) {
        return instances.values().stream()
                .filter(instance -> instance.status() == com.helios.sdk.model.WorkflowStatus.RUNNING)
                .filter(instance -> instance.updatedAt().isBefore(instant))
                .toList();
    }

    private ConcurrentMap<String, StepExecutionRecord> recordsFor(String workflowId) {
        return stepRecords.computeIfAbsent(workflowId, ignored -> new ConcurrentHashMap<>());
    }

    private String recordKey(String stepName, int attemptNumber) {
        return stepName + "#" + attemptNumber;
    }
}
