#!/usr/bin/env bash
set -euo pipefail

echo "Deploying chaos mesh resources..."
kubectl apply -f - <<EOF
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: orchestrator-kill
  namespace: helios-prod
spec:
  action: pod-kill
  mode: fixed-percent
  value: "30"
  selector:
    labelSelectors:
      app: helios-orchestrator
  duration: "30s"
  scheduler:
    cron: "@every 1m"
EOF
echo "Chaos test applied. Observe pod restarts."
