package com.helios.sdk;

import com.helios.sdk.model.TaskResult;
import com.helios.sdk.model.WorkflowStep;

@FunctionalInterface
public interface TaskHandler {
    TaskResult execute(WorkflowStep step) throws Exception;
}
