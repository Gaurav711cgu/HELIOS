package com.helios.orchestrator.core;

import com.helios.sdk.TaskHandler;
import com.helios.sdk.model.TaskResult;
import com.helios.sdk.model.TaskType;
import com.helios.sdk.model.WorkflowStep;

import java.util.EnumMap;
import java.util.Map;

public final class TaskRouter {
    private final Map<TaskType, TaskHandler> handlers = new EnumMap<>(TaskType.class);

    public TaskRouter() {
        TaskHandler noop = step -> {
            if (Boolean.TRUE.equals(step.input().get("fail"))) {
                throw new IllegalStateException("failure injected by step input");
            }
            return TaskResult.ok(Map.of("step", step.name(), "taskType", step.taskType().name()));
        };
        handlers.put(TaskType.NOOP, noop);
        handlers.put(TaskType.HTTP, noop);
        handlers.put(TaskType.GRPC, noop);
        handlers.put(TaskType.KAFKA_PRODUCE, noop);
        handlers.put(TaskType.COMPENSATION, noop);
    }

    public void register(TaskType type, TaskHandler handler) {
        handlers.put(type, handler);
    }

    public TaskResult execute(WorkflowStep step) throws Exception {
        TaskHandler handler = handlers.get(step.taskType());
        if (handler == null) {
            throw new IllegalArgumentException("no handler registered for task type " + step.taskType());
        }
        return handler.execute(step);
    }
}
