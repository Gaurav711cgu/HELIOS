package com.helios.orchestrator.core;

import com.helios.sdk.model.WorkflowDefinition;
import com.helios.sdk.model.WorkflowStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DagValidator {
    public ValidationResult validate(WorkflowDefinition definition) {
        List<String> errors = new ArrayList<>();
        Map<String, WorkflowStep> steps = new HashMap<>();

        for (WorkflowStep step : definition.steps()) {
            if (steps.put(step.name(), step) != null) {
                errors.add("duplicate step name: " + step.name());
            }
        }

        for (WorkflowStep step : definition.steps()) {
            for (String dependency : step.dependsOn()) {
                if (!steps.containsKey(dependency)) {
                    errors.add("step " + step.name() + " depends on missing step " + dependency);
                }
            }
            if (step.compensationStep() != null && !steps.containsKey(step.compensationStep())) {
                errors.add("step " + step.name() + " references missing compensation step " + step.compensationStep());
            }
        }

        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (WorkflowStep step : definition.steps()) {
            detectCycle(step.name(), steps, visiting, visited, errors);
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }

    private void detectCycle(
            String stepName,
            Map<String, WorkflowStep> steps,
            Set<String> visiting,
            Set<String> visited,
            List<String> errors
    ) {
        if (visited.contains(stepName) || !steps.containsKey(stepName)) {
            return;
        }
        if (!visiting.add(stepName)) {
            errors.add("cycle detected at step " + stepName);
            return;
        }
        for (String dependency : steps.get(stepName).dependsOn()) {
            detectCycle(dependency, steps, visiting, visited, errors);
        }
        visiting.remove(stepName);
        visited.add(stepName);
    }
}
