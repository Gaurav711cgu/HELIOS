package com.helios.orchestrator;

import com.helios.orchestrator.core.DagExecutor;
import com.helios.orchestrator.core.DagValidator;
import com.helios.orchestrator.core.EventBus;
import com.helios.orchestrator.core.InMemoryEventBus;
import com.helios.orchestrator.core.InMemoryWorkflowStateStore;
import com.helios.orchestrator.core.NaturalLanguageWorkflowSynthesizer;
import com.helios.orchestrator.core.TaskRouter;
import com.helios.orchestrator.core.ValidationResult;
import com.helios.orchestrator.core.WorkflowEvent;
import com.helios.orchestrator.core.WorkflowInstance;
import com.helios.orchestrator.core.WorkflowStateStore;
import com.helios.sdk.model.RetryPolicy;
import com.helios.sdk.model.TaskType;
import com.helios.sdk.model.WorkflowDefinition;
import com.helios.sdk.model.WorkflowStatus;
import com.helios.sdk.model.WorkflowStep;

import java.util.List;
import java.util.Map;

public final class CoreSelfTest {
    public static void main(String[] args) {
        validatesCycles();
        executesWorkflowOnce();
        replaysPendingAttemptWithSameIdempotencyKey();
        compensatesInReverseTopologicalOrder();
        synthesizesValidWorkflow();
        System.out.println("CoreSelfTest passed");
    }

    private static void replaysPendingAttemptWithSameIdempotencyKey() {
        WorkflowStateStore store = new InMemoryWorkflowStateStore();
        EventBus eventBus = new InMemoryEventBus();
        DagExecutor executor = new DagExecutor(store, new DagValidator(), new TaskRouter(), eventBus);
        WorkflowDefinition definition = new WorkflowDefinition("crash-window", "billing", List.of(
                WorkflowStep.builder("charge-card").taskType(TaskType.NOOP).build()
        ));
        store.registerDefinition(definition);
        WorkflowInstance instance = store.createInstance(definition.name(), definition.tenantId());
        store.markPending(instance.id(), "charge-card", 1, instance.id() + ":charge-card:1");

        executor.recover(instance);

        assertTrue(instance.status() == WorkflowStatus.COMPLETED, "recovered workflow should complete");
        assertTrue(store.stepsForWorkflow(instance.id()).size() == 1, "recovery should reuse pending attempt instead of creating attempt 2");
        assertTrue(store.stepsForWorkflow(instance.id()).getFirst().attemptNumber() == 1, "recovery should keep same idempotency key attempt");
    }

    private static void validatesCycles() {
        WorkflowDefinition invalid = new WorkflowDefinition("cycle", "tenant-a", List.of(
                WorkflowStep.builder("a").dependsOn("b").build(),
                WorkflowStep.builder("b").dependsOn("a").build()
        ));

        ValidationResult result = new DagValidator().validate(invalid);
        assertTrue(!result.valid(), "cycle should fail validation");
    }

    private static void executesWorkflowOnce() {
        WorkflowStateStore store = new InMemoryWorkflowStateStore();
        EventBus eventBus = new InMemoryEventBus();
        DagExecutor executor = new DagExecutor(store, new DagValidator(), new TaskRouter(), eventBus);
        WorkflowDefinition definition = new WorkflowDefinition("onboarding", "auth-service", List.of(
                WorkflowStep.builder("send-email").taskType(TaskType.HTTP).build(),
                WorkflowStep.builder("provision").taskType(TaskType.GRPC).dependsOn("send-email").build(),
                WorkflowStep.builder("notify-analytics").taskType(TaskType.KAFKA_PRODUCE).dependsOn("provision").build()
        ));
        store.registerDefinition(definition);

        WorkflowInstance instance = executor.trigger("onboarding");
        executor.recover(instance);

        assertTrue(instance.status() == WorkflowStatus.COMPLETED, "workflow should complete");
        long succeededTransitions = eventBus.events().stream()
                .filter(event -> event.type().equals("SUCCEEDED"))
                .count();
        assertTrue(succeededTransitions == 3, "recover should not duplicate completed steps");
    }

    private static void compensatesInReverseTopologicalOrder() {
        WorkflowStateStore store = new InMemoryWorkflowStateStore();
        EventBus eventBus = new InMemoryEventBus();
        DagExecutor executor = new DagExecutor(store, new DagValidator(), new TaskRouter(), eventBus);
        WorkflowDefinition definition = new WorkflowDefinition("payment-saga", "billing", List.of(
                WorkflowStep.builder("reserve-credit").taskType(TaskType.NOOP).compensationStep("release-credit").build(),
                WorkflowStep.builder("create-invoice").taskType(TaskType.NOOP).dependsOn("reserve-credit").compensationStep("void-invoice").build(),
                WorkflowStep.builder("capture-payment")
                        .taskType(TaskType.NOOP)
                        .dependsOn("create-invoice")
                        .retryPolicy(RetryPolicy.none())
                        .input(Map.of("fail", true))
                        .build(),
                WorkflowStep.builder("release-credit").taskType(TaskType.COMPENSATION).build(),
                WorkflowStep.builder("void-invoice").taskType(TaskType.COMPENSATION).build()
        ));
        store.registerDefinition(definition);

        WorkflowInstance instance = executor.trigger("payment-saga");
        assertTrue(instance.status() == WorkflowStatus.FAILED, "workflow should fail after terminal step failure");

        List<String> compensationOrder = eventBus.events().stream()
                .filter(event -> event.type().equals("COMPENSATION_TRIGGERED"))
                .map(WorkflowEvent::stepName)
                .toList();
        assertTrue(compensationOrder.equals(List.of("create-invoice", "reserve-credit")), "compensation should run in reverse topological order");
    }

    private static void synthesizesValidWorkflow() {
        NaturalLanguageWorkflowSynthesizer synthesizer = new NaturalLanguageWorkflowSynthesizer();
        ValidationResult result = synthesizer.synthesize(
                "auth-service",
                "user onboarding",
                "send a welcome email, then provision the free tier, then notify analytics"
        ).validation();
        assertTrue(result.valid(), "synthesized workflow should pass DAG validation");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
