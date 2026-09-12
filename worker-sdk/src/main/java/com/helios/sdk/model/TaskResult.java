package com.helios.sdk.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

public record TaskResult(boolean success, Map<String, Object> output, String message, Instant completedAt) implements Serializable {
    public TaskResult {
        output = output == null ? Map.of() : Map.copyOf(output);
        completedAt = completedAt == null ? Instant.now() : completedAt;
    }

    public static TaskResult ok(Map<String, Object> output) {
        return new TaskResult(true, output, "ok", Instant.now());
    }

    public static TaskResult failed(String message) {
        return new TaskResult(false, Map.of(), message, Instant.now());
    }
}
