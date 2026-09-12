package com.helios.orchestrator.core;

import java.util.List;

public interface EventBus {
    void publish(WorkflowEvent event);

    default void flush() {
    }

    List<WorkflowEvent> events();
}
