package com.helios.orchestrator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.helios.orchestrator.core.DagExecutor;
import com.helios.orchestrator.core.DagValidator;
import com.helios.orchestrator.core.NaturalLanguageWorkflowSynthesizer;
import com.helios.orchestrator.core.StepExecutionRecord;
import com.helios.orchestrator.core.SynthesisResult;
import com.helios.orchestrator.core.TaskRouter;
import com.helios.orchestrator.core.WorkflowEvent;
import com.helios.orchestrator.core.WorkflowInstance;
import com.helios.orchestrator.infrastructure.DynamoDbWorkflowStateStore;
import com.helios.orchestrator.infrastructure.KafkaEventBus;
import com.helios.orchestrator.infrastructure.RedisQuotaManager;
import com.helios.sdk.model.TaskType;
import com.helios.sdk.model.WorkflowDefinition;
import com.helios.sdk.model.WorkflowStatus;
import com.helios.sdk.model.WorkflowStep;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import redis.clients.jedis.JedisPooled;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class RealBenchmarkRunner {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
    private final String dynamoEndpoint = System.getProperty("helios.dynamodb.endpoint", "http://localhost:8000");
    private final String kafkaBootstrap = System.getProperty("helios.kafka.bootstrap", "localhost:9092");
    private final String redisHost = System.getProperty("helios.redis.host", "localhost");
    private final Path outputDir = Path.of(System.getProperty("helios.benchmark.outputDir", "benchmark-results"));
    private final int durationSeconds = Integer.parseInt(System.getProperty("helios.benchmark.durationSeconds", "300"));
    private final int targetRatePerSecond = Integer.parseInt(System.getProperty("helios.benchmark.targetRatePerSecond", "500"));
    private final int latencySamples = Integer.parseInt(System.getProperty("helios.benchmark.latencySamples", "1000"));

    public static void main(String[] args) throws Exception {
        new RealBenchmarkRunner().run();
    }

    private void run() throws Exception {
        Files.createDirectories(outputDir);
        Files.createDirectories(outputDir.resolve("throughput-gatling"));
        ensureKafkaTopics();

        String tableName = "helios-workflow-state-bench-" + System.currentTimeMillis();
        DynamoDbWorkflowStateStore stateStore = new DynamoDbWorkflowStateStore(dynamo(), mapper, tableName);
        KafkaEventBus eventBus = new KafkaEventBus(kafkaBootstrap, mapper);
        RedisQuotaManager quota = new RedisQuotaManager(new JedisPooled(redisHost, 6379), 1_000_000, 1_000_000);
        DagExecutor executor = new DagExecutor(stateStore, new DagValidator(), new TaskRouter(), eventBus, quota);

        WorkflowDefinition definition = benchmarkDefinition();
        stateStore.registerDefinition(definition);

        Map<String, Object> scheduling = runSchedulingLatency(executor, eventBus, definition);
        writeJson(outputDir.resolve("scheduling-latency-jmh.json"), scheduling);

        Map<String, Object> throughput = runThroughput(executor, eventBus, definition);
        writeJson(outputDir.resolve("throughput-gatling").resolve("real-throughput.json"), throughput);

        String chaos = runChaosReplay(stateStore, executor, definition);
        Files.writeString(outputDir.resolve("chaos-test-results.txt"), chaos, StandardCharsets.UTF_8);

        String recoveryCsv = runCrashRecoveryTrials(stateStore, executor, definition);
        Files.writeString(outputDir.resolve("crash-recovery-times.csv"), recoveryCsv, StandardCharsets.UTF_8);

        Map<String, Object> synthesis = runSynthesisValidation();
        writeJson(outputDir.resolve("ai-synthesis-accuracy.json"), synthesis);

        eventBus.flush();
        writeJson(outputDir.resolve("benchmark-summary.json"), Map.of(
                "generatedAt", Instant.now().toString(),
                "dynamoEndpoint", dynamoEndpoint,
                "kafkaBootstrap", kafkaBootstrap,
                "redisHost", redisHost,
                "tableName", tableName,
                "scheduling", scheduling,
                "throughput", throughput,
                "synthesis", synthesis
        ));
    }

    private Map<String, Object> runSchedulingLatency(DagExecutor executor, KafkaEventBus eventBus, WorkflowDefinition definition) {
        List<Long> latenciesMicros = new ArrayList<>();
        for (int i = 0; i < latencySamples; i++) {
            Instant start = Instant.now();
            WorkflowInstance instance = executor.trigger(definition.name());
            WorkflowEvent firstRunning = eventBus.events().stream()
                    .filter(event -> event.workflowId().equals(instance.id()))
                    .filter(event -> event.type().equals("RUNNING"))
                    .findFirst()
                    .orElseThrow();
            latenciesMicros.add(Duration.between(start, firstRunning.occurredAt()).toNanos() / 1_000);
        }
        return percentileResult("trigger-to-first-step", latenciesMicros, latencySamples);
    }

    private Map<String, Object> runThroughput(DagExecutor executor, KafkaEventBus eventBus, WorkflowDefinition definition) throws Exception {
        int planned = durationSeconds * targetRatePerSecond;
        AtomicInteger submitted = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<Long> latenciesMicros = Collections.synchronizedList(new ArrayList<>());
        Semaphore pacing = new Semaphore(0);

        Thread pacer = Thread.ofVirtual().start(() -> {
            long intervalNanos = 1_000_000_000L / Math.max(1, targetRatePerSecond);
            long next = System.nanoTime();
            for (int i = 0; i < planned; i++) {
                pacing.release();
                next += intervalNanos;
                long sleepNanos = next - System.nanoTime();
                if (sleepNanos > 0) {
                    try {
                        TimeUnit.NANOSECONDS.sleep(sleepNanos);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });

        Instant startedAt = Instant.now();
        long wallStart = System.nanoTime();
        try (ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < planned; i++) {
                pacing.acquire();
                submitted.incrementAndGet();
                futures.add(workers.submit(triggerWorkflow(executor, definition, completed, failed, latenciesMicros)));
            }
            for (Future<Void> future : futures) {
                future.get();
            }
        }
        pacer.join();
        eventBus.flush();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - wallStart);

        Map<String, Object> result = new LinkedHashMap<>(percentileResult("workflow-trigger", latenciesMicros, latenciesMicros.size()));
        result.put("startedAt", startedAt.toString());
        result.put("durationSecondsConfigured", durationSeconds);
        result.put("targetRatePerSecond", targetRatePerSecond);
        result.put("submitted", submitted.get());
        result.put("completed", completed.get());
        result.put("failed", failed.get());
        result.put("elapsedMillis", elapsedMillis);
        result.put("actualCompletedPerSecond", completed.get() / Math.max(1.0, elapsedMillis / 1000.0));
        result.put("realInfrastructure", List.of("DynamoDB Local", "Kafka", "Redis"));
        return result;
    }

    private Callable<Void> triggerWorkflow(
            DagExecutor executor,
            WorkflowDefinition definition,
            AtomicInteger completed,
            AtomicInteger failed,
            List<Long> latenciesMicros
    ) {
        return () -> {
            long start = System.nanoTime();
            try {
                WorkflowInstance instance = executor.trigger(definition.name());
                if (instance.status() == WorkflowStatus.COMPLETED) {
                    completed.incrementAndGet();
                } else {
                    failed.incrementAndGet();
                }
            } catch (Exception exception) {
                failed.incrementAndGet();
            } finally {
                latenciesMicros.add(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start));
            }
            return null;
        };
    }

    private String runChaosReplay(DynamoDbWorkflowStateStore stateStore, DagExecutor executor, WorkflowDefinition definition) {
        int duplicates = 0;
        int injections = 1_000;
        for (int i = 0; i < injections; i++) {
            WorkflowInstance instance = stateStore.createInstance(definition.name(), definition.tenantId());
            String key = instance.id() + ":step-a:1";
            stateStore.markPending(instance.id(), "step-a", 1, key);
            executor.recover(instance);
            long attempts = stateStore.stepsForWorkflow(instance.id()).stream()
                    .filter(record -> record.stepName().equals("step-a"))
                    .count();
            if (attempts != 1 || !stateStore.hasSucceeded(key)) {
                duplicates++;
            }
        }
        return "real_infrastructure=true\n"
                + "injections=" + injections + "\n"
                + "duplicate_step_executions=" + duplicates + "\n"
                + "passed=" + (duplicates == 0) + "\n";
    }

    private String runCrashRecoveryTrials(DynamoDbWorkflowStateStore stateStore, DagExecutor executor, WorkflowDefinition definition) {
        StringBuilder csv = new StringBuilder("trial,recoveryMillis,recoveredWorkflows\n");
        for (int trial = 1; trial <= 100; trial++) {
            List<WorkflowInstance> instances = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                WorkflowInstance instance = stateStore.createInstance(definition.name(), definition.tenantId());
                stateStore.markPending(instance.id(), "step-a", 1, instance.id() + ":step-a:1");
                instances.add(instance);
            }
            long start = System.nanoTime();
            for (WorkflowInstance instance : instances) {
                executor.recover(instance);
            }
            long recoveryMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            csv.append(trial).append(',').append(recoveryMillis).append(',').append(instances.size()).append('\n');
        }
        return csv.toString();
    }

    private Map<String, Object> runSynthesisValidation() {
        NaturalLanguageWorkflowSynthesizer synthesizer = new NaturalLanguageWorkflowSynthesizer();
        List<String> prompts = List.of(
                "send welcome email then provision account then notify analytics",
                "charge customer, then generate invoice, then email receipt",
                "validate signup, then create profile, then publish user created event",
                "reserve inventory, then charge card, then create shipment",
                "ingest file, then parse records, then write audit event",
                "fetch account data, then call risk service, then approve account",
                "create workspace, then invite owner, then send onboarding email",
                "sync crm contact, then publish kafka event, then notify sales",
                "validate order, then reserve credit, then submit order",
                "run model inference, then store result, then notify webhook",
                "receive image, then scan content, then update moderation queue",
                "create subscription, then provision plan, then send confirmation",
                "import users, then deduplicate users, then send report",
                "read payment event, then update ledger, then notify accounting",
                "create ticket, then assign owner, then send slack notification",
                "validate document, then archive document, then emit event",
                "start deployment, then wait for health check, then notify release channel",
                "create tenant, then seed defaults, then publish tenant ready event",
                "verify identity, then open account, then email customer",
                "receive webhook, then transform payload, then send kafka event",
                "send password reset email then record audit event",
                "provision database then provision service then notify owner",
                "cancel subscription then revoke entitlements then send email",
                "run backup then verify checksum then publish backup complete event",
                "approve expense then pay vendor then notify employee",
                "validate claim then request documents then update claim status",
                "prepare statement then email statement then archive copy",
                "create campaign then sync contacts then publish launch event",
                "receive support email then classify message then open case",
                "detect fraud then freeze card then notify risk team",
                "create pull request then run checks then notify reviewer",
                "parse log file then detect anomalies then create incident",
                "receive invoice then extract fields then post ledger entry",
                "generate report then upload to s3 then email link",
                "create customer then create billing profile then publish event",
                "close account then revoke tokens then notify compliance",
                "validate quote then generate contract then email signer",
                "receive shipment update then update order then notify customer",
                "start trial then provision sandbox then send getting started email",
                "scan repository then create findings then notify security",
                "create payout then verify bank then submit transfer",
                "ingest csv then validate rows then publish import result",
                "approve loan then create account then send disclosure email",
                "create meeting then generate agenda then email attendees",
                "receive refund request then validate policy then issue refund",
                "open incident then page responder then create status update",
                "compile metrics then write dashboard event then notify owner",
                "create api key then store secret then send usage guide",
                "rotate secret then restart service then notify platform team",
                "delete user then remove sessions then publish deletion event"
        );
        int valid = 0;
        List<Map<String, Object>> cases = new ArrayList<>();
        for (String prompt : prompts) {
            SynthesisResult result = synthesizer.synthesize("benchmark", "workflow-" + cases.size(), prompt);
            boolean passed = result.validation().valid();
            if (passed) {
                valid++;
            }
            cases.add(Map.of("prompt", prompt, "valid", passed, "steps", result.definition().steps().size()));
        }
        return Map.of(
                "realLlmUsed", false,
                "reason", "ANTHROPIC_API_KEY integration is not configured in this harness yet; result is schema-parser validation only and must not be used as AI claim.",
                "total", prompts.size(),
                "valid", valid,
                "firstPassValidationRate", valid / (double) prompts.size(),
                "cases", cases
        );
    }

    private Map<String, Object> percentileResult(String name, List<Long> values, int samples) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("benchmark", name);
        result.put("samples", samples);
        result.put("unit", "microseconds");
        result.put("p50", percentile(sorted, 0.50));
        result.put("p95", percentile(sorted, 0.95));
        result.put("p99", percentile(sorted, 0.99));
        result.put("min", sorted.isEmpty() ? 0 : sorted.getFirst());
        result.put("max", sorted.isEmpty() ? 0 : sorted.getLast());
        result.put("realInfrastructure", List.of("DynamoDB Local", "Kafka", "Redis"));
        return result;
    }

    private long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private WorkflowDefinition benchmarkDefinition() {
        return new WorkflowDefinition("benchmark-three-step", "benchmark-tenant", List.of(
                WorkflowStep.builder("step-a").taskType(TaskType.NOOP).build(),
                WorkflowStep.builder("step-b").taskType(TaskType.NOOP).dependsOn("step-a").build(),
                WorkflowStep.builder("step-c").taskType(TaskType.KAFKA_PRODUCE).dependsOn("step-b").build()
        ));
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
                    new NewTopic("helios.dlq", 3, (short) 1),
                    new NewTopic("helios.compensation", 3, (short) 1)
            )).all().get();
        } catch (Exception ignored) {
            // Existing topics are acceptable; broker reachability is exercised by producers below.
        }
    }

    private void writeJson(Path path, Object value) throws IOException {
        Files.writeString(path, mapper.writeValueAsString(value) + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
