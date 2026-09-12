#!/usr/bin/env bash
set -euo pipefail

DURATION_SECONDS=${DURATION_SECONDS:-300}
TARGET_RATE_PER_SECOND=${TARGET_RATE_PER_SECOND:-500}
LATENCY_SAMPLES=${LATENCY_SAMPLES:-1000}

if [[ -z "${AWS_REGION:-}" ]]; then
  echo "Set AWS_REGION, DYNAMODB_ENDPOINT, KAFKA_BOOTSTRAP, REDIS_HOST"
  exit 1
fi

docker run --rm \
  -v "$PWD:/workspace" \
  -w /workspace \
  gradle:8.10-jdk21 \
  gradle :orchestrator:realBenchmark --no-daemon \
    -Dhelios.dynamodb.endpoint="$DYNAMODB_ENDPOINT" \
    -Dhelios.aws.region="$AWS_REGION" \
    -Dhelios.kafka.bootstrap="$KAFKA_BOOTSTRAP" \
    -Dhelios.redis.host="$REDIS_HOST" \
    -Dhelios.benchmark.durationSeconds="$DURATION_SECONDS" \
    -Dhelios.benchmark.targetRatePerSecond="$TARGET_RATE_PER_SECOND" \
    -Dhelios.benchmark.latencySamples="$LATENCY_SAMPLES"
