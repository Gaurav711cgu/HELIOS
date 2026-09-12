# DynamoDB Single-Table Design

Helios persists workflow definitions, workflow instances, step attempts, tenant queries, and due schedules in one logical table.

## Table

`helios-workflow-state`

| Access pattern | PK | SK |
| --- | --- | --- |
| Workflow metadata | `WORKFLOW#{workflowId}` | `METADATA` |
| Step attempt | `WORKFLOW#{workflowId}` | `STEP#{stepName}#ATTEMPT#{attempt}` |
| Tenant workflow listing | `TENANT#{tenantId}` | `WORKFLOW#{createdAt}#{workflowId}` |
| Schedule lookup | `SCHEDULE#{bucket}` | `DUE#{scheduledAt}#{workflowId}` |

## Indexes

| Index | PK | SK | Purpose |
| --- | --- | --- | --- |
| `status-index` | `status` | `updatedAt` | Find stale or running workflows for recovery |
| `schedule-index` | `scheduledBucket` | `scheduledAt#workflowId` | Poll due workflows without scanning |
| `tenant-index` | `tenantId` | `createdAt#workflowId` | Paginated tenant workflow history |

## Exactly-Once Write Path

1. Compute `idempotencyKey = workflowId + ":" + stepName + ":" + attemptNumber`.
2. Write a `PENDING` attempt record before executing user code.
3. Execute the task.
4. Mark the attempt `SUCCEEDED` using a conditional write on `idempotencyKey`.
5. During crash recovery, any `PENDING` or stale `RUNNING` attempt is replayed with the same idempotency key.

The current implementation ships this behavior through `InMemoryWorkflowStateStore`; the production DynamoDB adapter is the next hardening step.
