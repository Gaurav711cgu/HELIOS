package com.helios.sdk.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record WorkflowStep(
        String name,
        TaskType taskType,
        List<String> dependsOn,
        RetryPolicy retryPolicy,
        long timeoutMillis,
        FailureMode failureMode,
        String compensationStep,
        Map<String, Object> input
) implements Serializable {
    public WorkflowStep {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("step name is required");
        }
        taskType = taskType == null ? TaskType.NOOP : taskType;
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        retryPolicy = retryPolicy == null ? RetryPolicy.none() : retryPolicy;
        timeoutMillis = timeoutMillis <= 0 ? 30_000 : timeoutMillis;
        failureMode = failureMode == null ? FailureMode.FAIL_WORKFLOW : failureMode;
        input = input == null ? Map.of() : Map.copyOf(input);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private TaskType taskType = TaskType.NOOP;
        private final List<String> dependsOn = new ArrayList<>();
        private RetryPolicy retryPolicy = RetryPolicy.none();
        private long timeoutMillis = 30_000;
        private FailureMode failureMode = FailureMode.FAIL_WORKFLOW;
        private String compensationStep;
        private Map<String, Object> input = Map.of();

        private Builder(String name) {
            this.name = name;
        }

        public Builder taskType(TaskType taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder dependsOn(String stepName) {
            this.dependsOn.add(stepName);
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public Builder failureMode(FailureMode failureMode) {
            this.failureMode = failureMode;
            return this;
        }

        public Builder compensationStep(String compensationStep) {
            this.compensationStep = compensationStep;
            return this;
        }

        public Builder input(Map<String, Object> input) {
            this.input = input;
            return this;
        }

        public WorkflowStep build() {
            return new WorkflowStep(name, taskType, dependsOn, retryPolicy, timeoutMillis, failureMode, compensationStep, input);
        }
    }
}
