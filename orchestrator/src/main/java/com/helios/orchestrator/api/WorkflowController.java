package com.helios.orchestrator.api;

import com.helios.orchestrator.core.DagExecutor;
import com.helios.orchestrator.core.DagValidator;
import com.helios.orchestrator.core.EventBus;
import com.helios.orchestrator.core.StepExecutionRecord;
import com.helios.orchestrator.core.SynthesisResult;
import com.helios.orchestrator.core.ValidationResult;
import com.helios.orchestrator.core.WorkflowInstance;
import com.helios.orchestrator.core.WorkflowStateStore;
import com.helios.orchestrator.core.WorkflowSynthesizer;
import com.helios.sdk.model.WorkflowDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class WorkflowController {
    private final WorkflowStateStore stateStore;
    private final DagValidator validator;
    private final DagExecutor executor;
    private final WorkflowSynthesizer synthesizer;
    private final EventBus eventBus;

    public WorkflowController(
            WorkflowStateStore stateStore,
            DagValidator validator,
            DagExecutor executor,
            WorkflowSynthesizer synthesizer,
            EventBus eventBus
    ) {
        this.stateStore = stateStore;
        this.validator = validator;
        this.executor = executor;
        this.synthesizer = synthesizer;
        this.eventBus = eventBus;
    }

    @PostMapping("/workflows/definitions")
    public ResponseEntity<?> registerDefinition(@RequestBody WorkflowDefinition definition) {
        ValidationResult validation = validator.validate(definition);
        if (!validation.valid()) {
            return ResponseEntity.badRequest().body(validation);
        }
        stateStore.registerDefinition(definition);
        return ResponseEntity.accepted().body(Map.of("name", definition.name(), "tenantId", definition.tenantId(), "steps", definition.steps().size()));
    }

    @PostMapping("/workflows/synthesize")
    public SynthesisResult synthesize(@RequestBody SynthesisRequest request) {
        return synthesizer.synthesize(request.tenantId(), request.workflowName(), request.naturalLanguage());
    }

    @PostMapping("/workflows/trigger")
    public WorkflowView trigger(@RequestBody TriggerRequest request) {
        WorkflowInstance instance = executor.trigger(request.definitionName());
        return WorkflowView.from(instance, stateStore.stepsForWorkflow(instance.id()));
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<WorkflowView> getWorkflow(@PathVariable String id) {
        return stateStore.getInstance(id)
                .map(instance -> ResponseEntity.ok(WorkflowView.from(instance, stateStore.stepsForWorkflow(instance.id()))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/workflows/{id}/timeline")
    public List<StepExecutionRecord> timeline(@PathVariable String id) {
        return stateStore.stepsForWorkflow(id);
    }

    @GetMapping("/tenants/{tenantId}/workflows")
    public List<WorkflowView> tenantWorkflows(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "25") int limit
    ) {
        return stateStore.listTenantWorkflows(tenantId, offset, limit).stream()
                .map(instance -> WorkflowView.from(instance, stateStore.stepsForWorkflow(instance.id())))
                .toList();
    }

    @GetMapping("/metrics/throughput")
    public Map<String, Object> throughput() {
        long stepEvents = eventBus.events().stream()
                .filter(event -> event.topic().equals("helios.step.events"))
                .count();
        long dlqEvents = eventBus.events().stream()
                .filter(event -> event.topic().equals("helios.dlq"))
                .count();
        return Map.of("stepEvents", stepEvents, "dlqDepth", dlqEvents);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
