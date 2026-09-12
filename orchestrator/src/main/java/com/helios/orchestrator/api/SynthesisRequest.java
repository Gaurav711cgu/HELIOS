package com.helios.orchestrator.api;

public record SynthesisRequest(String tenantId, String workflowName, String naturalLanguage) {
}
