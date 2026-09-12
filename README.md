# Helios

Helios is a Java-native durable workflow engine for backend teams that need crash-safe background orchestration, dependency-aware DAG execution, tenant fairness, Kafka eventing, and natural-language workflow synthesis.

The project is intentionally positioned against durable execution systems, not visual automation tools. n8n connects SaaS applications for operators. Helios runs backend business workflows with persisted state, idempotency keys, retry semantics, and recovery paths.

## Current Status

Helios now has a real production path and a real test rig:

| Area | Status |
| --- | --- |
| Workflow model and Java SDK | Implemented |
| DAG validation | Implemented |
| Virtual-thread execution engine | Implemented |
| Retry and idempotency key handling | Implemented |
| Saga compensation order | Implemented |
| DynamoDB Local state adapter | Implemented and integration-tested |
| Kafka event bus | Implemented and integration-tested |
| Redis tenant quota adapter | Implemented and integration-tested |
| Spring Boot REST API | Implemented |
| Static frontend/API console | Implemented |
| Real benchmark harness | Implemented |
| Claude/Anthropic production synthesis client | Not claimed yet; parser-backed synthesis exists |
| Formal JMH/Gatling reports | Not claimed yet; JavaExec real-infra benchmark exists |
| Kubernetes/Helm/multi-node chaos | Not claimed yet |

## Measured Results

These results were generated locally on Docker Desktop against real containers: DynamoDB Local, Kafka 3.7.0, and Redis 7.4. They are committed under [benchmark-results](/Users/gauravkumarnayak/Desktop/HELIOS/benchmark-results/README.md).

| Benchmark | Result |
| --- | --- |
| Integration suite | Passed: DynamoDB state, Kafka events/DLQ, Redis quota |
| Scheduling latency calibration | P50 10.559 ms, P95 22.992 ms, P99 113.688 ms |
| Throughput calibration | 1,000 submitted, 1,000 completed, 0 failed |
| Sustained throughput observed | 48.13 workflows/sec at configured 50/sec for 20 seconds |
| Workflow trigger P99 under calibration | 1.156 seconds |
| Chaos replay | 1,000 crash-window replays, 0 duplicate step attempts |
| Crash recovery | 100 trials, 100 in-flight workflows per trial, observed range 1.080-1.780 seconds |
| Synthesis validation | 50/50 parser-backed valid DAGs; not an LLM claim |

The repository does not claim the original 500 workflows/sec target yet. The current measured ceiling on this laptop/container setup is the calibration result above. The full benchmark command is present and can be run on stronger hardware.

## Architecture

```text
REST or SDK clients
        |
        v
Spring Boot Orchestrator
  - DAG validation
  - Virtual-thread execution
  - Retry and idempotency handling
  - Saga compensation
  - Tenant quota admission
        |
        +--> DynamoDB: workflow definitions, instances, step attempts, access-pattern indexes
        +--> Kafka: step events, DLQ events, compensation events
        +--> Redis: token-bucket tenant quotas
        +--> Prometheus/Grafana: metrics path and dashboard target
```

## Repository Layout

| Path | Purpose |
| --- | --- |
| [worker-sdk](/Users/gauravkumarnayak/Desktop/HELIOS/worker-sdk/src/main/java/com/helios/sdk/model/WorkflowDefinition.java) | Java workflow model and task handler API |
| [orchestrator/core](/Users/gauravkumarnayak/Desktop/HELIOS/orchestrator/src/main/java/com/helios/orchestrator/core/DagExecutor.java) | Engine logic independent of infrastructure |
| [orchestrator/infrastructure](/Users/gauravkumarnayak/Desktop/HELIOS/orchestrator/src/main/java/com/helios/orchestrator/infrastructure/DynamoDbWorkflowStateStore.java) | Real DynamoDB, Kafka, and Redis adapters |
| [orchestrator/api](/Users/gauravkumarnayak/Desktop/HELIOS/orchestrator/src/main/java/com/helios/orchestrator/api/WorkflowController.java) | Spring Boot API |
| [orchestrator/src/integrationTest](/Users/gauravkumarnayak/Desktop/HELIOS/orchestrator/src/integrationTest/java/com/helios/orchestrator/integration/RealInfrastructureIntegrationTest.java) | Real integration tests |
| [website](/Users/gauravkumarnayak/Desktop/HELIOS/website/index.html) | Populated frontend and API console |
| [scripts](/Users/gauravkumarnayak/Desktop/HELIOS/scripts/run-real-benchmark.sh) | Repeatable Docker-based test and benchmark commands |
| [benchmark-results](/Users/gauravkumarnayak/Desktop/HELIOS/benchmark-results/benchmark-summary.json) | Generated benchmark artifacts |

## Backend Setup

Prerequisites:

- Docker Desktop running.
- Java 21 or newer if running outside Docker.
- Gradle is optional on the host because the scripts use the official Gradle Docker image.

Start real local infrastructure:

```bash
docker compose up -d dynamodb-local redis kafka
```

Run the real integration test suite:

```bash
./scripts/run-real-integration.sh
```

Run the backend in production-infrastructure mode:

```bash
docker run --rm \
  --network helios_default \
  -p 8080:8080 \
  -v "$PWD:/workspace" \
  -w /workspace \
  gradle:8.10-jdk21 \
  gradle :orchestrator:bootRun --no-daemon \
    --args='--helios.infrastructure.mode=real --helios.dynamodb.endpoint=http://dynamodb-local:8000 --helios.kafka.bootstrap-servers=kafka:29092 --helios.redis.host=redis'
```

The backend listens on:

```text
http://localhost:8080
```

## Frontend Setup

The frontend is static and lives in [website/index.html](/Users/gauravkumarnayak/Desktop/HELIOS/website/index.html). Open it directly in a browser or serve it from any static server.

For a local static server:

```bash
cd website
python3 -m http.server 5173
```

Then open:

```text
http://localhost:5173
```

The frontend calls the backend URL from the input field at the top of the API console. Default:

```text
http://localhost:8080
```

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/workflows/definitions` | Register and validate a workflow definition |
| `POST` | `/api/v1/workflows/synthesize` | Convert text into a draft DAG using the current parser-backed synthesizer |
| `POST` | `/api/v1/workflows/trigger` | Start a workflow instance |
| `GET` | `/api/v1/workflows/{id}` | Fetch workflow state |
| `GET` | `/api/v1/workflows/{id}/timeline` | Fetch step attempts |
| `GET` | `/api/v1/tenants/{tenantId}/workflows` | List workflows by tenant |
| `GET` | `/api/v1/metrics/throughput` | Read event counters |
| `GET` | `/api/v1/health` | Readiness endpoint |

## Example Workflow Definition

```json
{
  "name": "user-onboarding",
  "tenantId": "auth-service",
  "steps": [
    {
      "name": "send-welcome-email",
      "taskType": "HTTP",
      "dependsOn": [],
      "retryPolicy": { "maxAttempts": 3, "initialBackoffMillis": 200, "multiplier": 2.0 },
      "timeoutMillis": 30000,
      "failureMode": "FAIL_WORKFLOW",
      "input": {}
    },
    {
      "name": "provision-free-tier",
      "taskType": "GRPC",
      "dependsOn": ["send-welcome-email"],
      "retryPolicy": { "maxAttempts": 1, "initialBackoffMillis": 0, "multiplier": 1.0 },
      "timeoutMillis": 30000,
      "failureMode": "FAIL_WORKFLOW",
      "input": {}
    },
    {
      "name": "notify-analytics",
      "taskType": "KAFKA_PRODUCE",
      "dependsOn": ["provision-free-tier"],
      "retryPolicy": { "maxAttempts": 1, "initialBackoffMillis": 0, "multiplier": 1.0 },
      "timeoutMillis": 30000,
      "failureMode": "IGNORE",
      "input": {}
    }
  ]
}
```

## Benchmarks

Run a full benchmark:

```bash
./scripts/run-real-benchmark.sh
```

Run a calibration benchmark:

```bash
DURATION_SECONDS=20 TARGET_RATE_PER_SECOND=50 LATENCY_SAMPLES=100 ./scripts/run-real-benchmark.sh
```

Generated files:

| File | Meaning |
| --- | --- |
| [benchmark-summary.json](/Users/gauravkumarnayak/Desktop/HELIOS/benchmark-results/benchmark-summary.json) | Consolidated real-infrastructure benchmark output |
| [scheduling-latency-jmh.json](/Users/gauravkumarnayak/Desktop/HELIOS/benchmark-results/scheduling-latency-jmh.json) | Trigger-to-first-step latency output from the Java benchmark harness |
| [throughput-gatling/real-throughput.json](/Users/gauravkumarnayak/Desktop/HELIOS/benchmark-results/throughput-gatling/real-throughput.json) | Sustained trigger throughput output |
| [chaos-test-results.txt](/Users/gauravkumarnayak/Desktop/HELIOS/benchmark-results/chaos-test-results.txt) | Crash-window replay idempotency result |
| [crash-recovery-times.csv](/Users/gauravkumarnayak/Desktop/HELIOS/benchmark-results/crash-recovery-times.csv) | 100 crash recovery timing trials |
| [ai-synthesis-accuracy.json](/Users/gauravkumarnayak/Desktop/HELIOS/benchmark-results/ai-synthesis-accuracy.json) | Parser validation dataset; not a real LLM result |

## Production Readiness Bar

To claim FAANG-level production readiness, these must be green:

1. Real integration tests pass against DynamoDB, Kafka, and Redis.
2. Benchmark artifacts are generated from the real harness and committed.
3. 1,000 crash-window replay injections produce zero duplicate step attempts.
4. Crash recovery P99 is under 5 seconds for 100 in-flight workflows.
5. Kafka step events and DLQ events are verified by a real consumer.
6. Redis tenant quotas reject excess load for a saturated tenant.
7. DynamoDB access patterns use keyed queries, not scans, on the hot path.
8. Any LLM synthesis accuracy claim is produced by a real API-backed harness with the model name and date recorded.

The first six items are implemented and verified in the current local run. Item seven is implemented for idempotency, status, tenant, and schedule indexes. Item eight is intentionally not claimed yet.
