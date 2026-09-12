package com.helios.orchestrator.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helios.orchestrator.core.StepExecutionRecord;
import com.helios.orchestrator.core.WorkflowInstance;
import com.helios.orchestrator.core.WorkflowStateStore;
import com.helios.sdk.model.StepStatus;
import com.helios.sdk.model.TaskResult;
import com.helios.sdk.model.WorkflowDefinition;
import com.helios.sdk.model.WorkflowStatus;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DynamoDbWorkflowStateStore implements WorkflowStateStore {
    public static final String STATUS_INDEX = "status-index";
    public static final String SCHEDULE_INDEX = "schedule-index";
    public static final String TENANT_INDEX = "tenant-index";
    public static final String IDEMPOTENCY_INDEX = "idempotency-index";

    private final DynamoDbClient dynamoDb;
    private final ObjectMapper mapper;
    private final String tableName;

    public DynamoDbWorkflowStateStore(DynamoDbClient dynamoDb, ObjectMapper mapper, String tableName) {
        this.dynamoDb = dynamoDb;
        this.mapper = mapper;
        this.tableName = tableName;
        ensureTable();
    }

    @Override
    public void registerDefinition(WorkflowDefinition definition) {
        put(item(
                "pk", "DEFINITION#" + definition.name(),
                "sk", "METADATA",
                "recordType", "WORKFLOW_DEFINITION",
                "name", definition.name(),
                "tenantId", definition.tenantId(),
                "payload", writeJson(definition)
        ));
    }

    @Override
    public Optional<WorkflowDefinition> getDefinition(String name) {
        return get("DEFINITION#" + name, "METADATA")
                .map(item -> readJson(item.get("payload").s(), WorkflowDefinition.class));
    }

    @Override
    public WorkflowInstance createInstance(String definitionName, String tenantId) {
        WorkflowInstance instance = new WorkflowInstance(UUID.randomUUID().toString(), definitionName, tenantId, Map.of());
        String created = instance.createdAt().toString();
        put(item(
                "pk", workflowPk(instance.id()),
                "sk", "METADATA",
                "recordType", "WORKFLOW_INSTANCE",
                "workflowId", instance.id(),
                "definitionName", instance.definitionName(),
                "tenantId", instance.tenantId(),
                "status", instance.status().name(),
                "updatedAt", instance.updatedAt().toString(),
                "createdAt", created,
                "gsi1pk", instance.status().name(),
                "gsi1sk", instance.updatedAt().toString(),
                "gsi3pk", tenantId,
                "gsi3sk", created + "#" + instance.id()
        ));
        return instance;
    }

    @Override
    public Optional<WorkflowInstance> getInstance(String workflowId) {
        return get(workflowPk(workflowId), "METADATA").map(this::toInstance);
    }

    @Override
    public void markWorkflowStatus(String workflowId, WorkflowStatus status) {
        updateInstanceStatus(workflowId, status);
    }

    @Override
    public StepExecutionRecord markPending(String workflowId, String stepName, int attemptNumber, String idempotencyKey) {
        StepExecutionRecord record = new StepExecutionRecord(workflowId, stepName, attemptNumber, idempotencyKey, StepStatus.PENDING);
        put(stepItem(record));
        return record;
    }

    @Override
    public void markRunning(String workflowId, String stepName, int attemptNumber) {
        updateStepStatus(workflowId, stepName, attemptNumber, StepStatus.RUNNING, Map.of("startedAt", Instant.now().toString()));
        updateInstanceStatus(workflowId, WorkflowStatus.RUNNING);
    }

    @Override
    public void markSucceeded(String workflowId, String stepName, int attemptNumber, TaskResult result) {
        updateStepStatus(workflowId, stepName, attemptNumber, StepStatus.SUCCEEDED, Map.of(
                "completedAt", result.completedAt().toString(),
                "output", writeJson(result.output())
        ));
    }

    @Override
    public void markFailed(String workflowId, String stepName, int attemptNumber, String errorMessage) {
        updateStepStatus(workflowId, stepName, attemptNumber, StepStatus.FAILED, Map.of(
                "completedAt", Instant.now().toString(),
                "errorMessage", errorMessage == null ? "" : errorMessage
        ));
    }

    @Override
    public boolean hasSucceeded(String idempotencyKey) {
        Map<String, AttributeValue> values = Map.of(":key", s(idempotencyKey), ":status", s(StepStatus.SUCCEEDED.name()));
        return dynamoDb.query(builder -> builder.tableName(tableName)
                        .indexName(IDEMPOTENCY_INDEX)
                        .keyConditionExpression("gsi4pk = :key")
                        .filterExpression("#status = :status")
                        .expressionAttributeNames(Map.of("#status", "status"))
                        .expressionAttributeValues(values)
                        .limit(1))
                .count() > 0;
    }

    @Override
    public int nextAttempt(String workflowId, String stepName) {
        return stepsForWorkflow(workflowId).stream()
                .filter(record -> record.stepName().equals(stepName))
                .mapToInt(StepExecutionRecord::attemptNumber)
                .max()
                .orElse(0) + 1;
    }

    @Override
    public Optional<StepExecutionRecord> latestStep(String workflowId, String stepName) {
        return stepsForWorkflow(workflowId).stream()
                .filter(record -> record.stepName().equals(stepName))
                .max(java.util.Comparator.comparingInt(StepExecutionRecord::attemptNumber));
    }

    @Override
    public List<StepExecutionRecord> stepsForWorkflow(String workflowId) {
        Map<String, AttributeValue> values = Map.of(":pk", s(workflowPk(workflowId)), ":prefix", s("STEP#"));
        return dynamoDb.query(builder -> builder.tableName(tableName)
                        .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
                        .expressionAttributeValues(values))
                .items().stream()
                .map(this::toStepRecord)
                .sorted(java.util.Comparator.comparing(StepExecutionRecord::stepName).thenComparingInt(StepExecutionRecord::attemptNumber))
                .toList();
    }

    @Override
    public List<WorkflowInstance> listTenantWorkflows(String tenantId, int offset, int limit) {
        Map<String, AttributeValue> values = Map.of(":tenant", s(tenantId));
        return dynamoDb.query(builder -> builder.tableName(tableName)
                        .indexName(TENANT_INDEX)
                        .keyConditionExpression("gsi3pk = :tenant")
                        .expressionAttributeValues(values)
                        .scanIndexForward(false)
                        .limit(Math.max(1, offset + limit)))
                .items().stream()
                .skip(Math.max(0, offset))
                .limit(Math.max(1, limit))
                .map(this::toInstance)
                .toList();
    }

    @Override
    public List<WorkflowInstance> runningBefore(Instant instant) {
        Map<String, AttributeValue> values = Map.of(":status", s(WorkflowStatus.RUNNING.name()), ":updatedAt", s(instant.toString()));
        return dynamoDb.query(builder -> builder.tableName(tableName)
                        .indexName(STATUS_INDEX)
                        .keyConditionExpression("gsi1pk = :status AND gsi1sk < :updatedAt")
                        .expressionAttributeValues(values))
                .items().stream()
                .map(this::toInstance)
                .toList();
    }

    public List<WorkflowInstance> dueScheduled(String bucket, int limit) {
        Map<String, AttributeValue> values = Map.of(":bucket", s(bucket));
        return dynamoDb.query(builder -> builder.tableName(tableName)
                        .indexName(SCHEDULE_INDEX)
                        .keyConditionExpression("gsi2pk = :bucket")
                        .expressionAttributeValues(values)
                        .limit(limit))
                .items().stream()
                .map(this::toInstance)
                .toList();
    }

    private void ensureTable() {
        try {
            dynamoDb.describeTable(builder -> builder.tableName(tableName));
            return;
        } catch (ResourceNotFoundException ignored) {
            // create below
        }
        dynamoDb.createTable(CreateTableRequest.builder()
                .tableName(tableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        attr("pk"), attr("sk"),
                        attr("gsi1pk"), attr("gsi1sk"),
                        attr("gsi2pk"), attr("gsi2sk"),
                        attr("gsi3pk"), attr("gsi3sk"),
                        attr("gsi4pk"), attr("gsi4sk"))
                .keySchema(key("pk", KeyType.HASH), key("sk", KeyType.RANGE))
                .globalSecondaryIndexes(
                        gsi(STATUS_INDEX, "gsi1pk", "gsi1sk"),
                        gsi(SCHEDULE_INDEX, "gsi2pk", "gsi2sk"),
                        gsi(TENANT_INDEX, "gsi3pk", "gsi3sk"),
                        gsi(IDEMPOTENCY_INDEX, "gsi4pk", "gsi4sk"))
                .build());
        dynamoDb.waiter().waitUntilTableExists(builder -> builder.tableName(tableName));
    }

    private GlobalSecondaryIndex gsi(String name, String pk, String sk) {
        return GlobalSecondaryIndex.builder()
                .indexName(name)
                .keySchema(key(pk, KeyType.HASH), key(sk, KeyType.RANGE))
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();
    }

    private AttributeDefinition attr(String name) {
        return AttributeDefinition.builder().attributeName(name).attributeType(ScalarAttributeType.S).build();
    }

    private KeySchemaElement key(String name, KeyType keyType) {
        return KeySchemaElement.builder().attributeName(name).keyType(keyType).build();
    }

    private Map<String, AttributeValue> item(String... pairs) {
        Map<String, AttributeValue> item = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                item.put(pairs[i], s(pairs[i + 1]));
            }
        }
        return item;
    }

    private Map<String, AttributeValue> stepItem(StepExecutionRecord record) {
        return item(
                "pk", workflowPk(record.workflowId()),
                "sk", stepSk(record.stepName(), record.attemptNumber()),
                "recordType", "STEP_ATTEMPT",
                "workflowId", record.workflowId(),
                "stepName", record.stepName(),
                "attemptNumber", Integer.toString(record.attemptNumber()),
                "idempotencyKey", record.idempotencyKey(),
                "gsi4pk", record.idempotencyKey(),
                "gsi4sk", record.createdAt().toString(),
                "status", record.status().name(),
                "createdAt", record.createdAt().toString()
        );
    }

    private void put(Map<String, AttributeValue> item) {
        dynamoDb.putItem(builder -> builder.tableName(tableName).item(item));
    }

    private Optional<Map<String, AttributeValue>> get(String pk, String sk) {
        Map<String, AttributeValue> item = dynamoDb.getItem(builder -> builder.tableName(tableName).key(Map.of("pk", s(pk), "sk", s(sk)))).item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(item);
    }

    private void updateStepStatus(String workflowId, String stepName, int attemptNumber, StepStatus status, Map<String, String> extras) {
        Map<String, AttributeValue> values = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        names.put("#status", "status");
        values.put(":status", s(status.name()));
        values.put(":updatedAt", s(Instant.now().toString()));
        StringBuilder expression = new StringBuilder("SET #status = :status, updatedAt = :updatedAt");
        int index = 0;
        for (Map.Entry<String, String> entry : extras.entrySet()) {
            String nameKey = "#n" + index;
            String valueKey = ":v" + index;
            names.put(nameKey, entry.getKey());
            values.put(valueKey, s(entry.getValue()));
            expression.append(", ").append(nameKey).append(" = ").append(valueKey);
            index++;
        }
        dynamoDb.updateItem(builder -> builder.tableName(tableName)
                .key(Map.of("pk", s(workflowPk(workflowId)), "sk", s(stepSk(stepName, attemptNumber))))
                .updateExpression(expression.toString())
                .expressionAttributeNames(names)
                .expressionAttributeValues(values));
    }

    private void updateInstanceStatus(String workflowId, WorkflowStatus status) {
        Instant now = Instant.now();
        dynamoDb.updateItem(builder -> builder.tableName(tableName)
                .key(Map.of("pk", s(workflowPk(workflowId)), "sk", s("METADATA")))
                .updateExpression("SET #status = :status, updatedAt = :updatedAt, gsi1pk = :status, gsi1sk = :updatedAt")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(":status", s(status.name()), ":updatedAt", s(now.toString()))));
    }

    private WorkflowInstance toInstance(Map<String, AttributeValue> item) {
        WorkflowInstance instance = new WorkflowInstance(
                item.get("workflowId").s(),
                item.get("definitionName").s(),
                item.get("tenantId").s(),
                Map.of());
        instance.status(WorkflowStatus.valueOf(item.get("status").s()));
        return instance;
    }

    private StepExecutionRecord toStepRecord(Map<String, AttributeValue> item) {
        StepExecutionRecord record = new StepExecutionRecord(
                item.get("workflowId").s(),
                item.get("stepName").s(),
                Integer.parseInt(item.get("attemptNumber").s()),
                item.get("idempotencyKey").s(),
                StepStatus.valueOf(item.get("status").s()));
        StepStatus status = StepStatus.valueOf(item.get("status").s());
        if (status == StepStatus.RUNNING) {
            record.markRunning();
        } else if (status == StepStatus.SUCCEEDED) {
            record.markSucceeded(TaskResult.ok(Map.of()));
        } else if (status == StepStatus.FAILED) {
            record.markFailed(item.getOrDefault("errorMessage", s("")).s());
        }
        return record;
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to parse DynamoDB payload", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize DynamoDB payload", exception);
        }
    }

    private AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private String workflowPk(String workflowId) {
        return "WORKFLOW#" + workflowId;
    }

    private String stepSk(String stepName, int attemptNumber) {
        return "STEP#" + stepName + "#ATTEMPT#" + attemptNumber;
    }
}
