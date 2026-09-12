package com.helios.orchestrator.core;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

public record WorkflowEvent(String topic, String workflowId, String stepName, String type, Instant occurredAt, Map<String, Object> attributes) implements Serializable {
    public WorkflowEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
