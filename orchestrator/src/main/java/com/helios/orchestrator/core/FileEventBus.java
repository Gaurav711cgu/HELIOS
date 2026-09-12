package com.helios.orchestrator.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FileEventBus implements EventBus {
    private final Path eventLog;
    private final CopyOnWriteArrayList<WorkflowEvent> events = new CopyOnWriteArrayList<>();

    public FileEventBus(Path eventLog) {
        this.eventLog = eventLog;
    }

    @Override
    public void publish(WorkflowEvent event) {
        events.add(event);
        try {
            Files.createDirectories(eventLog.getParent());
            Files.writeString(eventLog, encode(event) + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to append event log " + eventLog, exception);
        }
    }

    @Override
    public List<WorkflowEvent> events() {
        return List.copyOf(events);
    }

    private String encode(WorkflowEvent event) {
        return event.occurredAt() + "\t" + event.topic() + "\t" + event.workflowId() + "\t" + event.stepName() + "\t" + event.type() + "\t" + event.attributes();
    }
}
