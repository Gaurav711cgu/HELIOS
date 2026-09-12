package com.helios.orchestrator.core;

import com.helios.sdk.model.WorkflowDefinition;
import com.helios.sdk.model.WorkflowStep;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class SagaCompensator {
    private final TaskRouter taskRouter;
    private final EventBus eventBus;

    public SagaCompensator(TaskRouter taskRouter, EventBus eventBus) {
        this.taskRouter = taskRouter;
        this.eventBus = eventBus;
    }

    public List<String> compensate(WorkflowInstance instance, WorkflowDefinition definition, WorkflowStep failedStep, List<StepExecutionRecord> records) {
        List<WorkflowStep> completed = records.stream()
                .filter(record -> record.status() == com.helios.sdk.model.StepStatus.SUCCEEDED)
                .map(record -> definition.step(record.stepName()).orElseThrow())
                .filter(step -> step.compensationStep() != null)
                .sorted(Comparator.comparingInt((WorkflowStep step) -> dependencyDepth(step, definition)).reversed())
                .toList();

        List<String> compensated = new ArrayList<>();
        for (WorkflowStep step : completed) {
            WorkflowStep compensation = definition.step(step.compensationStep()).orElseThrow();
            eventBus.publish(new WorkflowEvent("helios.compensation", instance.id(), step.name(), "COMPENSATION_TRIGGERED", Instant.now(), Map.of("failedStep", failedStep.name())));
            try {
                taskRouter.execute(compensation);
                eventBus.publish(new WorkflowEvent("helios.compensation", instance.id(), compensation.name(), "COMPENSATED", Instant.now(), Map.of("originalStep", step.name())));
                compensated.add(step.name());
            } catch (Exception exception) {
                eventBus.publish(new WorkflowEvent("helios.dlq", instance.id(), compensation.name(), "COMPENSATION_FAILED", Instant.now(), Map.of("error", exception.getMessage())));
            }
        }
        return compensated;
    }

    private int dependencyDepth(WorkflowStep step, WorkflowDefinition definition) {
        if (step.dependsOn().isEmpty()) {
            return 0;
        }
        int max = 0;
        for (String dependency : step.dependsOn()) {
            WorkflowStep dependencyStep = definition.step(dependency).orElseThrow();
            max = Math.max(max, 1 + dependencyDepth(dependencyStep, definition));
        }
        return max;
    }
}
