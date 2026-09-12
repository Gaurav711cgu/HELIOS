package com.helios.orchestrator.core;

import com.helios.sdk.model.StepStatus;
import com.helios.sdk.model.TaskResult;
import com.helios.sdk.model.WorkflowDefinition;
import com.helios.sdk.model.WorkflowStatus;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DurableWorkflowStateStore implements WorkflowStateStore {
    private final Path snapshotPath;
    private final Map<String, WorkflowDefinition> definitions;
    private final Map<String, WorkflowInstance> instances;
    private final Map<String, Map<String, StepExecutionRecord>> stepRecords;
    private final Set<String> succeededIdempotencyKeys;

    public DurableWorkflowStateStore(Path snapshotPath) {
        this.snapshotPath = snapshotPath;
        StateSnapshot snapshot = load(snapshotPath);
        this.definitions = new LinkedHashMap<>(snapshot.definitions());
        this.instances = new LinkedHashMap<>(snapshot.instances());
        this.stepRecords = new LinkedHashMap<>(snapshot.stepRecords());
        this.succeededIdempotencyKeys = ConcurrentHashSet.copyOf(snapshot.succeededIdempotencyKeys());
    }

    @Override
    public synchronized void registerDefinition(WorkflowDefinition definition) {
        definitions.put(definition.name(), definition);
        persist();
    }

    @Override
    public synchronized Optional<WorkflowDefinition> getDefinition(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    @Override
    public synchronized WorkflowInstance createInstance(String definitionName, String tenantId) {
        WorkflowInstance instance = new WorkflowInstance(UUID.randomUUID().toString(), definitionName, tenantId, Map.of());
        instances.put(instance.id(), instance);
        stepRecords.put(instance.id(), new LinkedHashMap<>());
        persist();
        return instance;
    }

    @Override
    public synchronized Optional<WorkflowInstance> getInstance(String workflowId) {
        return Optional.ofNullable(instances.get(workflowId));
    }

    @Override
    public synchronized void markWorkflowStatus(String workflowId, WorkflowStatus status) {
        getInstance(workflowId).ifPresent(instance -> instance.status(status));
        persist();
    }

    @Override
    public synchronized StepExecutionRecord markPending(String workflowId, String stepName, int attemptNumber, String idempotencyKey) {
        StepExecutionRecord record = new StepExecutionRecord(workflowId, stepName, attemptNumber, idempotencyKey, StepStatus.PENDING);
        recordsFor(workflowId).put(recordKey(stepName, attemptNumber), record);
        persist();
        return record;
    }

    @Override
    public synchronized void markRunning(String workflowId, String stepName, int attemptNumber) {
        recordsFor(workflowId).get(recordKey(stepName, attemptNumber)).markRunning();
        markWorkflowStatus(workflowId, WorkflowStatus.RUNNING);
        persist();
    }

    @Override
    public synchronized void markSucceeded(String workflowId, String stepName, int attemptNumber, TaskResult result) {
        StepExecutionRecord record = recordsFor(workflowId).get(recordKey(stepName, attemptNumber));
        record.markSucceeded(result);
        succeededIdempotencyKeys.add(record.idempotencyKey());
        persist();
    }

    @Override
    public synchronized void markFailed(String workflowId, String stepName, int attemptNumber, String errorMessage) {
        recordsFor(workflowId).get(recordKey(stepName, attemptNumber)).markFailed(errorMessage);
        persist();
    }

    @Override
    public synchronized boolean hasSucceeded(String idempotencyKey) {
        return succeededIdempotencyKeys.contains(idempotencyKey);
    }

    @Override
    public synchronized int nextAttempt(String workflowId, String stepName) {
        return recordsFor(workflowId).values().stream()
                .filter(record -> record.stepName().equals(stepName))
                .mapToInt(StepExecutionRecord::attemptNumber)
                .max()
                .orElse(0) + 1;
    }

    @Override
    public synchronized Optional<StepExecutionRecord> latestStep(String workflowId, String stepName) {
        return recordsFor(workflowId).values().stream()
                .filter(record -> record.stepName().equals(stepName))
                .max(Comparator.comparingInt(StepExecutionRecord::attemptNumber));
    }

    @Override
    public synchronized List<StepExecutionRecord> stepsForWorkflow(String workflowId) {
        return recordsFor(workflowId).values().stream()
                .sorted(Comparator.comparing(StepExecutionRecord::stepName).thenComparingInt(StepExecutionRecord::attemptNumber))
                .toList();
    }

    @Override
    public synchronized List<WorkflowInstance> listTenantWorkflows(String tenantId, int offset, int limit) {
        return instances.values().stream()
                .filter(instance -> instance.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(WorkflowInstance::createdAt).reversed())
                .skip(Math.max(0, offset))
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public synchronized List<WorkflowInstance> runningBefore(Instant instant) {
        return instances.values().stream()
                .filter(instance -> instance.status() == WorkflowStatus.RUNNING)
                .filter(instance -> instance.updatedAt().isBefore(instant))
                .toList();
    }

    private Map<String, StepExecutionRecord> recordsFor(String workflowId) {
        return stepRecords.computeIfAbsent(workflowId, ignored -> new LinkedHashMap<>());
    }

    private String recordKey(String stepName, int attemptNumber) {
        return stepName + "#" + attemptNumber;
    }

    private void persist() {
        try {
            Files.createDirectories(snapshotPath.getParent());
            Path tmp = snapshotPath.resolveSibling(snapshotPath.getFileName() + ".tmp");
            try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(tmp))) {
                output.writeObject(new StateSnapshot(definitions, instances, stepRecords, succeededIdempotencyKeys));
            }
            Files.move(tmp, snapshotPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to persist workflow state to " + snapshotPath, exception);
        }
    }

    private StateSnapshot load(Path path) {
        if (!Files.exists(path)) {
            return StateSnapshot.empty();
        }
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(path))) {
            return (StateSnapshot) input.readObject();
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("failed to load workflow state from " + path, exception);
        }
    }

    private record StateSnapshot(
            Map<String, WorkflowDefinition> definitions,
            Map<String, WorkflowInstance> instances,
            Map<String, Map<String, StepExecutionRecord>> stepRecords,
            Set<String> succeededIdempotencyKeys
    ) implements Serializable {
        static StateSnapshot empty() {
            return new StateSnapshot(Map.of(), Map.of(), Map.of(), Set.of());
        }
    }

    private static final class ConcurrentHashSet {
        private ConcurrentHashSet() {
        }

        static Set<String> copyOf(Set<String> values) {
            Set<String> set = java.util.concurrent.ConcurrentHashMap.newKeySet(values.size() + 1);
            set.addAll(values);
            return set;
        }
    }
}
