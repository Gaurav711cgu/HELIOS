package com.helios.orchestrator.core;

import com.helios.sdk.model.FailureMode;
import com.helios.sdk.model.StepStatus;
import com.helios.sdk.model.TaskResult;
import com.helios.sdk.model.TaskType;
import com.helios.sdk.model.WorkflowDefinition;
import com.helios.sdk.model.WorkflowStatus;
import com.helios.sdk.model.WorkflowStep;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class DagExecutor {
    private final WorkflowStateStore stateStore;
    private final DagValidator validator;
    private final TaskRouter taskRouter;
    private final EventBus eventBus;
    private final SagaCompensator compensator;
    private final QuotaManager quotaManager;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public DagExecutor(WorkflowStateStore stateStore, DagValidator validator, TaskRouter taskRouter, EventBus eventBus) {
        this(stateStore, validator, taskRouter, eventBus, new TenantQuotaManager(10_000, 10_000));
    }

    public DagExecutor(WorkflowStateStore stateStore, DagValidator validator, TaskRouter taskRouter, EventBus eventBus, QuotaManager quotaManager) {
        this.stateStore = stateStore;
        this.validator = validator;
        this.taskRouter = taskRouter;
        this.eventBus = eventBus;
        this.quotaManager = quotaManager;
        this.compensator = new SagaCompensator(stateStore, taskRouter, eventBus);
    }

    public WorkflowInstance start(String definitionName, String tenantId) {
        WorkflowDefinition definition = stateStore.getDefinition(definitionName)
                .orElseThrow(() -> new IllegalArgumentException("workflow definition not found: " + definitionName));

        validator.validate(definition);
        quotaManager.checkQuota(tenantId);

        WorkflowInstance instance = stateStore.createInstance(definitionName, tenantId);
        instance.status(WorkflowStatus.RUNNING);
        stateStore.markWorkflowStatus(instance.id(), WorkflowStatus.RUNNING);

        execute(instance, definition);
        return instance;
    }

    public void recover(WorkflowInstance instance) {
        WorkflowDefinition definition = stateStore.getDefinition(instance.definitionName())
                .orElseThrow(() -> new IllegalArgumentException("workflow definition not found: " + instance.definitionName()));
        execute(instance, definition);
    }

    public void execute(WorkflowInstance instance, WorkflowDefinition definition) {
        executor.submit(() -> evaluate(instance, definition));
    }

    private void evaluate(WorkflowInstance instance, WorkflowDefinition definition) {
        synchronized (instance.id().intern()) {
            if (instance.status() != WorkflowStatus.RUNNING) {
                return;
            }

            List<WorkflowStep> readySteps = readySteps(instance, definition);
            if (readySteps.isEmpty()) {
                if (allStepsSucceeded(instance, definition)) {
                    instance.status(WorkflowStatus.COMPLETED);
                    stateStore.markWorkflowStatus(instance.id(), WorkflowStatus.COMPLETED);
                }
                return;
            }

            for (WorkflowStep step : readySteps) {
                int attemptNumber = attemptForExecution(instance.id(), step.name());
                String idempotencyKey = idempotencyKey(instance.id(), step.name(), attemptNumber);
                
                if (!hasReplayableAttempt(instance.id(), step.name(), attemptNumber)) {
                    stateStore.markPending(instance.id(), step.name(), attemptNumber, idempotencyKey);
                    publish(instance.id(), step.name(), "PENDING", Map.of("attempt", attemptNumber));
                } else {
                    publish(instance.id(), step.name(), "RECOVERY_REPLAY", Map.of("attempt", attemptNumber));
                }
                
                executor.submit(() -> {
                    try {
                        executeStep(instance, definition, step, attemptNumber, idempotencyKey);
                    } finally {
                        evaluate(instance, definition);
                    }
                });
            }
        }
    }

    private void executeStep(WorkflowInstance instance, WorkflowDefinition definition, WorkflowStep step, int initialAttempt, String initialKey) {
        int attemptNumber = initialAttempt;
        String idempotencyKey = initialKey;
        
        while (attemptNumber <= step.retryPolicy().maxAttempts()) {
            if (stateStore.hasSucceeded(idempotencyKey)) {
                return;
            }

            if (attemptNumber > initialAttempt) {
                if (!hasReplayableAttempt(instance.id(), step.name(), attemptNumber)) {
                    stateStore.markPending(instance.id(), step.name(), attemptNumber, idempotencyKey);
                    publish(instance.id(), step.name(), "PENDING", Map.of("attempt", attemptNumber));
                } else {
                    publish(instance.id(), step.name(), "RECOVERY_REPLAY", Map.of("attempt", attemptNumber));
                }
            }

            stateStore.markRunning(instance.id(), step.name(), attemptNumber);
            publish(instance.id(), step.name(), "RUNNING", Map.of("attempt", attemptNumber));

            try {
                maybeBackoff(step, attemptNumber);
                TaskResult result = taskRouter.execute(step);
                if (!result.success()) {
                    throw new IllegalStateException(result.message());
                }
                stateStore.markSucceeded(instance.id(), step.name(), attemptNumber, result);
                publish(instance.id(), step.name(), "SUCCEEDED", Map.of("attempt", attemptNumber));
                return;
            } catch (Exception exception) {
                stateStore.markFailed(instance.id(), step.name(), attemptNumber, exception.getMessage());
                publish(instance.id(), step.name(), "FAILED", Map.of("attempt", attemptNumber, "error", exception.getMessage()));
                if (attemptNumber >= step.retryPolicy().maxAttempts()) {
                    handleTerminalFailure(instance, definition, step, exception);
                    return;
                }
                attemptNumber = stateStore.nextAttempt(instance.id(), step.name());
                idempotencyKey = idempotencyKey(instance.id(), step.name(), attemptNumber);
            }
        }
    }

    private int attemptForExecution(String workflowId, String stepName) {
        return stateStore.latestStep(workflowId, stepName)
                .filter(record -> record.status() == StepStatus.PENDING || record.status() == StepStatus.RUNNING)
                .map(StepExecutionRecord::attemptNumber)
                .orElseGet(() -> stateStore.nextAttempt(workflowId, stepName));
    }

    private boolean hasReplayableAttempt(String workflowId, String stepName, int attemptNumber) {
        return stateStore.latestStep(workflowId, stepName)
                .filter(record -> record.attemptNumber() == attemptNumber)
                .filter(record -> record.status() == StepStatus.PENDING || record.status() == StepStatus.RUNNING)
                .isPresent();
    }

    private void handleTerminalFailure(WorkflowInstance instance, WorkflowDefinition definition, WorkflowStep step, Exception exception) {
        eventBus.publish(new WorkflowEvent("helios.dlq", instance.id(), step.name(), "DLQ", Instant.now(), Map.of("error", exception.getMessage())));
        if (step.failureMode() == FailureMode.IGNORE) {
            int nextAttempt = stateStore.nextAttempt(instance.id(), step.name());
            stateStore.markPending(instance.id(), step.name(), nextAttempt, idempotencyKey(instance.id(), step.name(), nextAttempt));
            stateStore.markSucceeded(instance.id(), step.name(), nextAttempt, TaskResult.ok(Map.of("ignoredFailure", true)));
            publish(instance.id(), step.name(), "IGNORED_FAILURE", Map.of());
            return;
        }
        instance.status(WorkflowStatus.COMPENSATING);
        stateStore.markWorkflowStatus(instance.id(), WorkflowStatus.COMPENSATING);
        compensator.compensate(instance, definition, step, stateStore.stepsForWorkflow(instance.id()));
        instance.status(WorkflowStatus.FAILED);
        stateStore.markWorkflowStatus(instance.id(), WorkflowStatus.FAILED);
    }

    private List<WorkflowStep> readySteps(WorkflowInstance instance, WorkflowDefinition definition) {
        List<WorkflowStep> ready = new ArrayList<>();
        Set<String> completed = completedSteps(instance.id());
        for (WorkflowStep step : definition.steps()) {
            if (step.taskType() == TaskType.COMPENSATION) {
                continue;
            }
            
            StepStatus status = latestStatus(instance.id(), step.name());
            if (status != StepStatus.WAITING) {
                continue;
            }
            
            if (completed.containsAll(step.dependsOn())) {
                ready.add(step);
            }
        }
        return ready;
    }

    private boolean allStepsSucceeded(WorkflowInstance instance, WorkflowDefinition definition) {
        Set<String> completed = completedSteps(instance.id());
        return definition.steps().stream()
                .filter(step -> step.taskType() != TaskType.COMPENSATION)
                .allMatch(step -> completed.contains(step.name()));
    }

    private Set<String> completedSteps(String workflowId) {
        Set<String> completed = new HashSet<>();
        for (StepExecutionRecord record : stateStore.stepsForWorkflow(workflowId)) {
            if (record.status() == StepStatus.SUCCEEDED) {
                completed.add(record.stepName());
            }
        }
        return completed;
    }

    private StepStatus latestStatus(String workflowId, String stepName) {
        return stateStore.latestStep(workflowId, stepName)
                .map(StepExecutionRecord::status)
                .orElse(StepStatus.WAITING);
    }

    private void publish(String workflowId, String stepName, String type, Map<String, Object> attributes) {
        eventBus.publish(new WorkflowEvent("helios.step.events", workflowId, stepName, type, Instant.now(), attributes));
    }

    private void maybeBackoff(WorkflowStep step, int attemptNumber) throws InterruptedException {
        long delay = step.retryPolicy().delayForAttempt(attemptNumber);
        if (delay > 0) {
            Thread.sleep(delay);
        }
    }

    private String idempotencyKey(String workflowId, String stepName, int attemptNumber) {
        return workflowId + ":" + stepName + ":" + attemptNumber;
    }

    private void waitFor(Future<?> future) {
        try {
            future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("workflow execution interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("workflow execution failed", exception.getCause());
        }
    }
}
