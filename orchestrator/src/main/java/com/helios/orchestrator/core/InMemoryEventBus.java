package com.helios.orchestrator.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryEventBus implements EventBus {
    private final CopyOnWriteArrayList<WorkflowEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void publish(WorkflowEvent event) {
        events.add(event);
    }

    @Override
    public List<WorkflowEvent> events() {
        return List.copyOf(events);
    }
}
