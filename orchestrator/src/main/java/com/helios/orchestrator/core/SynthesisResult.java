package com.helios.orchestrator.core;

import com.helios.sdk.model.WorkflowDefinition;

public record SynthesisResult(WorkflowDefinition definition, ValidationResult validation, String explanation) {
}
