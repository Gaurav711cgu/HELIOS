package com.helios.orchestrator.core;

import com.helios.sdk.model.TaskType;
import com.helios.sdk.model.WorkflowDefinition;
import com.helios.sdk.model.WorkflowStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NaturalLanguageWorkflowSynthesizer implements WorkflowSynthesizer {
    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\bthen\\b|,|;|\\band then\\b", Pattern.CASE_INSENSITIVE);

    public SynthesisResult synthesize(String tenantId, String workflowName, String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        String safeName = workflowName == null || workflowName.isBlank() ? "ai-synthesized-workflow" : slug(workflowName);
        List<WorkflowStep> steps = new ArrayList<>();
        String previous = null;
        int index = 1;
        for (String rawPart : SPLIT_PATTERN.split(description)) {
            String phrase = rawPart.trim();
            if (phrase.isBlank() || phrase.toLowerCase(Locale.ROOT).startsWith("if ")) {
                continue;
            }
            String stepName = slug(phrase);
            if (stepName.length() > 48) {
                stepName = stepName.substring(0, 48);
            }
            if (stepName.isBlank()) {
                stepName = "step-" + index;
            }
            WorkflowStep.Builder builder = WorkflowStep.builder(stepName).taskType(inferTaskType(phrase));
            if (previous != null) {
                builder.dependsOn(previous);
            }
            steps.add(builder.build());
            previous = stepName;
            index++;
        }
        if (steps.isEmpty()) {
            steps.add(WorkflowStep.builder("execute-request").taskType(TaskType.NOOP).build());
        }
        WorkflowDefinition definition = new WorkflowDefinition(safeName, tenantId == null || tenantId.isBlank() ? "default" : tenantId, steps);
        ValidationResult validation = new DagValidator().validate(definition);
        return new SynthesisResult(definition, validation, "Heuristic synthesis created a sequential DAG. Replace this component with structured Claude output for M8.");
    }

    private TaskType inferTaskType(String phrase) {
        String lower = phrase.toLowerCase(Locale.ROOT);
        if (lower.contains("email") || lower.contains("http") || lower.contains("webhook")) {
            return TaskType.HTTP;
        }
        if (lower.contains("kafka") || lower.contains("event") || lower.contains("notify analytics")) {
            return TaskType.KAFKA_PRODUCE;
        }
        if (lower.contains("grpc") || lower.contains("provision")) {
            return TaskType.GRPC;
        }
        return TaskType.NOOP;
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
