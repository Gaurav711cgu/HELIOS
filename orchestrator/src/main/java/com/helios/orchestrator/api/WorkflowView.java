package com.helios.orchestrator.api;

import com.helios.orchestrator.core.StepExecutionRecord;
import com.helios.orchestrator.core.WorkflowInstance;
import com.helios.sdk.model.WorkflowStatus;

import java.time.Instant;
import java.util.List;

public record WorkflowView(
        String id,
        String definitionName,
        String tenantId,
        WorkflowStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<StepExecutionRecord> steps
) {
    public static WorkflowView from(WorkflowInstance instance, List<StepExecutionRecord> steps) {
        return new WorkflowView(
                instance.id(),
                instance.definitionName(),
                instance.tenantId(),
                instance.status(),
                instance.createdAt(),
                instance.updatedAt(),
                steps
        );
    }
}
