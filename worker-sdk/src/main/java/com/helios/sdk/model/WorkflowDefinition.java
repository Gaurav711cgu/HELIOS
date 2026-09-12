package com.helios.sdk.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record WorkflowDefinition(String name, String tenantId, List<WorkflowStep> steps) implements Serializable {
    public WorkflowDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("workflow name is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("at least one step is required");
        }
        steps = List.copyOf(steps);
    }

    public Optional<WorkflowStep> step(String name) {
        return steps.stream().filter(step -> step.name().equals(name)).findFirst();
    }

    public Map<String, WorkflowStep> stepIndex() {
        Map<String, WorkflowStep> index = new LinkedHashMap<>();
        for (WorkflowStep step : steps) {
            index.put(step.name(), step);
        }
        return Map.copyOf(index);
    }
}
