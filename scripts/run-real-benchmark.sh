#!/usr/bin/env bash
set -euo pipefail

DURATION_SECONDS="${DURATION_SECONDS:-300}"
TARGET_RATE_PER_SECOND="${TARGET_RATE_PER_SECOND:-500}"
LATENCY_SAMPLES="${LATENCY_SAMPLES:-1000}"

docker compose up -d dynamodb-local redis kafka
docker run --rm \
  --network helios_default \
  -v "$PWD:/workspace" \
  -w /workspace \
  gradle:8.10-jdk21 \
  gradle :orchestrator:realBenchmark --no-daemon --stacktrace \
    -Dhelios.dynamodb.endpoint=http://dynamodb-local:8000 \
    -Dhelios.kafka.bootstrap=kafka:29092 \
    -Dhelios.redis.host=redis \
    -Dhelios.benchmark.outputDir=benchmark-results \
    -Dhelios.benchmark.durationSeconds="$DURATION_SECONDS" \
    -Dhelios.benchmark.targetRatePerSecond="$TARGET_RATE_PER_SECOND" \
    -Dhelios.benchmark.latencySamples="$LATENCY_SAMPLES"
