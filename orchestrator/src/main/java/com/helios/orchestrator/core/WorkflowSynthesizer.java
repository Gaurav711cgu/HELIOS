package com.helios.orchestrator.core;

public interface WorkflowSynthesizer {
    SynthesisResult synthesize(String tenantId, String workflowName, String description);
}
