package com.helios.orchestrator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helios.orchestrator.core.DagExecutor;
import com.helios.orchestrator.core.DagValidator;
import com.helios.orchestrator.core.TaskRouter;
import com.helios.orchestrator.core.WorkflowInstance;
import com.helios.orchestrator.infrastructure.DynamoDbWorkflowStateStore;
import com.helios.orchestrator.infrastructure.KafkaEventBus;
import com.helios.orchestrator.infrastructure.RedisQuotaManager;
import com.helios.sdk.model.RetryPolicy;
import com.helios.sdk.model.TaskType;
import com.helios.sdk.model.WorkflowDefinition;
import com.helios.sdk.model.WorkflowStatus;
import com.helios.sdk.model.WorkflowStep;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RealInfrastructureIntegrationTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final String dynamoEndpoint = System.getProperty("helios.dynamodb.endpoint", "http://localhost:8000");
    private final String kafkaBootstrap = System.getProperty("helios.kafka.bootstrap", "localhost:9092");
    private final String redisHost = System.getProperty("helios.redis.host", "localhost");

    @Test
    void executesWorkflowUsingRealDynamoDbKafkaAndRedis() {
        ensureKafkaTopics();
        String tableName = "helios-workflow-state-it-" + System.currentTimeMillis();
        DynamoDbWorkflowStateStore stateStore = new DynamoDbWorkflowStateStore(dynamo(), mapper, tableName);
        KafkaEventBus eventBus = new KafkaEventBus(kafkaBootstrap, mapper);
        RedisQuotaManager quota = new RedisQuotaManager(new JedisPooled(redisHost, 6379), 100, 100);
        DagExecutor executor = new DagExecutor(stateStore, new DagValidator(), new TaskRouter(), eventBus, quota);

        WorkflowDefinition definition = new WorkflowDefinition("real-infra-onboarding", "tenant-real", List.of(
                WorkflowStep.builder("send-email").taskType(TaskType.HTTP).build(),
                WorkflowStep.builder("provision").taskType(TaskType.GRPC).dependsOn("send-email").build(),
                WorkflowStep.builder("notify").taskType(TaskType.KAFKA_PRODUCE).dependsOn("provision").build()
        ));

        stateStore.registerDefinition(definition);
        WorkflowInstance instance = executor.trigger(definition.name());
        eventBus.flush();

        assertEquals(WorkflowStatus.COMPLETED, stateStore.getInstance(instance.id()).orElseThrow().status());
        assertEquals(3, stateStore.stepsForWorkflow(instance.id()).stream().filter(step -> step.status().name().equals("SUCCEEDED")).count());
        assertFalse(stateStore.listTenantWorkflows("tenant-real", 0, 10).isEmpty());
        assertTrue(stateStore.runningBefore(Instant.now().plusSeconds(60)).isEmpty());
        assertTrue(readKafkaEvents("helios.step.events", instance.id(), 9));
    }

    @Test
    void sendsTerminalFailuresToRealKafkaDlq() {
        ensureKafkaTopics();
        String tableName = "helios-workflow-state-dlq-it-" + System.currentTimeMillis();
        DynamoDbWorkflowStateStore stateStore = new DynamoDbWorkflowStateStore(dynamo(), mapper, tableName);
        KafkaEventBus eventBus = new KafkaEventBus(kafkaBootstrap, mapper);
        RedisQuotaManager quota = new RedisQuotaManager(new JedisPooled(redisHost, 6379), 100, 100);
        DagExecutor executor = new DagExecutor(stateStore, new DagValidator(), new TaskRouter(), eventBus, quota);

        WorkflowDefinition definition = new WorkflowDefinition("real-dlq", "tenant-real", List.of(
                WorkflowStep.builder("always-fail")
                        .taskType(TaskType.NOOP)
                        .retryPolicy(RetryPolicy.none())
                        .input(Map.of("fail", true))
                        .build()
        ));

        stateStore.registerDefinition(definition);
        WorkflowInstance instance = executor.trigger(definition.name());
        eventBus.flush();

        assertEquals(WorkflowStatus.FAILED, stateStore.getInstance(instance.id()).orElseThrow().status());
        assertTrue(readKafkaEvents("helios.dlq", instance.id(), 1));
    }

    @Test
    void enforcesTenantQuotaInRealRedis() {
        JedisPooled jedis = new JedisPooled(redisHost, 6379);
        String tenant = "quota-it-" + System.currentTimeMillis();
        RedisQuotaManager quota = new RedisQuotaManager(jedis, 2, 0.1);

        assertTrue(quota.tryAcquire(tenant));
        assertTrue(quota.tryAcquire(tenant));
        assertFalse(quota.tryAcquire(tenant));
    }

    private DynamoDbClient dynamo() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(dynamoEndpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
                .build();
    }

    private void ensureKafkaTopics() {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaBootstrap);
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(List.of(
                    new NewTopic("helios.step.events", 6, (short) 1),
                    new NewTopic("helios.dlq", 3, (short) 1)
            )).all().get();
        } catch (Exception ignored) {
            // Topics may already exist.
        }
    }

    private boolean readKafkaEvents(String topic, String workflowId, int expectedMinimum) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "helios-it-" + topic + "-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        int seen = 0;
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Set.of(topic));
            while (System.nanoTime() < deadline && seen < expectedMinimum) {
                for (var record : consumer.poll(Duration.ofMillis(250))) {
                    if (workflowId.equals(record.key())) {
                        seen++;
                    }
                }
            }
        }
        return seen >= expectedMinimum;
    }
}
