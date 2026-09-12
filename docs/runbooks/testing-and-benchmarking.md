# Testing And Benchmarking Runbook

This project separates three verification levels.

## 1. Core Engine Self-Test

Use this when checking local Java logic without external services:

```bash
javac -d /private/tmp/helios-classes \
  worker-sdk/src/main/java/com/helios/sdk/*.java \
  worker-sdk/src/main/java/com/helios/sdk/model/*.java \
  orchestrator/src/main/java/com/helios/orchestrator/core/*.java \
  orchestrator/src/test/java/com/helios/orchestrator/CoreSelfTest.java

java -cp /private/tmp/helios-classes com.helios.orchestrator.CoreSelfTest
```

This validates DAG cycles, idempotency replay, compensation order, and parser-backed synthesis. It is not a production benchmark.

## 2. Real Infrastructure Integration Tests

Use this before claiming the backend works end to end:

```bash
./scripts/run-real-integration.sh
```

The script starts Docker Compose services and runs Gradle integration tests against:

- DynamoDB Local.
- Kafka.
- Redis.

The integration suite verifies:

- Workflow definitions and step attempts persist through the DynamoDB adapter.
- Step transition events and DLQ events are published to Kafka and read by a real Kafka consumer.
- Tenant quota enforcement uses Redis.

## 3. Real Infrastructure Benchmarks

Use this to generate claimable artifacts:

```bash
./scripts/run-real-benchmark.sh
```

For a short calibration run:

```bash
DURATION_SECONDS=20 TARGET_RATE_PER_SECOND=50 LATENCY_SAMPLES=100 ./scripts/run-real-benchmark.sh
```

Generated files are written to `benchmark-results/`.

Do not claim numbers that are not present in those files. The current local run supports a 50 workflows/sec calibration claim, crash-window replay idempotency, and sub-5-second recovery for the tested workload. It does not support a 500 workflows/sec claim yet.

## Backend And Frontend Connection

Run the backend in real-infrastructure mode:

```bash
docker compose up -d dynamodb-local redis kafka

docker run --rm \
  --network helios_default \
  -p 8080:8080 \
  -v "$PWD:/workspace" \
  -w /workspace \
  gradle:8.10-jdk21 \
  gradle :orchestrator:bootRun --no-daemon \
    --args='--helios.infrastructure.mode=real --helios.dynamodb.endpoint=http://dynamodb-local:8000 --helios.kafka.bootstrap-servers=kafka:29092 --helios.redis.host=redis'
```

Serve the frontend:

```bash
cd website
python3 -m http.server 5173
```

Open `http://localhost:5173` and keep the Backend URL field set to `http://localhost:8080`.
