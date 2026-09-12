#!/usr/bin/env bash
set -euo pipefail

docker compose up -d dynamodb-local redis kafka
docker run --rm \
  --network helios_default \
  -v "$PWD:/workspace" \
  -w /workspace \
  gradle:8.10-jdk21 \
  gradle :orchestrator:integrationTest --no-daemon --stacktrace \
    -Dhelios.dynamodb.endpoint=http://dynamodb-local:8000 \
    -Dhelios.kafka.bootstrap=kafka:29092 \
    -Dhelios.redis.host=redis
