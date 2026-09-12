'use client';
import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { CheckCircle, Loader, AlertTriangle, Zap, Database, Radio, Server } from 'lucide-react';

// ─── Data ───────────────────────────────────────────────────────────────────

const PHASES = [
  {
    id: 0,
    step: '01',
    name: 'HTTP Trigger',
    icon: Zap,
    tagline: 'A workflow begins life as an HTTP POST.',
    duration: '3 ms',
    detail: `A client calls POST /api/v1/workflows/{tenantId}/trigger with a JSON payload containing the workflow name and input parameters. Spring Boot's DispatcherServlet routes the request to WorkflowController, which extracts the tenant ID from the path and validates the JWT bearer token for quota eligibility before forwarding to the engine.`,
    code: `POST /api/v1/workflows/auth-service/trigger
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "workflowName": "user-onboarding",
  "input": { "userId": "usr_9a2f1b" }
}`,
    difficulties: [
      {
        problem: 'Rate limiting per tenant was inconsistent under burst load',
        solution: 'Moved quota enforcement to a Redis INCR + EXPIRE atomic pattern. Each tenant gets a sliding 1-second window key. Atomicity ensured by Lua script — no race condition between check and increment.',
      },
      {
        problem: 'JWT validation added 18ms of latency on every request',
        solution: 'Public key cached in-memory on first load. Subsequent validations are pure cryptographic operations — no network round trip. Added to Spring security filter chain with a 0-allocation fast-path for valid tokens.',
      },
    ],
    outcome: 'Request authenticated, tenant quota checked, workflow definition resolved.',
  },
  {
    id: 1,
    step: '02',
    name: 'DAG Validation',
    icon: Server,
    tagline: 'The workflow definition is parsed and validated as a Directed Acyclic Graph.',
    duration: '8 ms',
    detail: `The workflow definition (loaded from the registered definition store) is parsed into a DagDefinition object. DagValidator performs three checks: (1) topological sort to confirm the graph is acyclic, (2) reachability analysis to confirm every step is reachable from the trigger and leads to a terminal step, (3) step type validation to confirm all step handlers are registered. If any check fails, a 422 Unprocessable Entity is returned with the specific failure location.`,
    code: `// DagValidator.java
public ValidationResult validate(DagDefinition dag) {
  // 1. Topological sort — fails if cycle detected
  List<String> order = TopologicalSort.sort(dag.getSteps());
  
  // 2. Reachability from trigger → all terminal steps  
  Set<String> reachable = BFS.reachable(dag, dag.getTrigger());
  
  // 3. All steps must have registered handlers
  dag.getSteps().forEach(step -> 
    StepRegistry.requireHandler(step.getType()));
  
  return ValidationResult.ok(order);
}`,
    difficulties: [
      {
        problem: 'Cycle detection in customer-defined DAGs caused stack overflow for large graphs',
        solution: 'Replaced recursive DFS with iterative Kahn\'s algorithm. Uses an explicit deque — no call stack growth. Also added a node limit (max 500 steps per workflow) enforced before validation begins.',
      },
      {
        problem: 'Invalid step references only surfaced at execution time — too late',
        solution: 'Moved step handler existence check into the validation phase. StepRegistry is queried eagerly at definition registration time and cached. Unknown step types fail fast with a descriptive error naming the unresolvable step ID.',
      },
    ],
    outcome: 'Validated execution order produced. Workflow ready for persistence.',
  },
  {
    id: 2,
    step: '03',
    name: 'State Persistence',
    icon: Database,
    tagline: 'Execution state is written to DynamoDB with conditional exactly-once semantics.',
    duration: '24 ms',
    detail: `WorkflowStateStore writes the initial workflow state to DynamoDB using a conditional put_item expression: the write only succeeds if no item with the same workflowId exists (attribute_not_exists(workflowId)). This is the cornerstone of exactly-once execution — even if the orchestrator crashes immediately after this write and restarts, the recovery path will see the existing item and resume rather than re-create. The item contains: workflowId, tenantId, currentStep, status=PENDING, version=0, createdAt, and the full step graph serialised as a DynamoDB Map.`,
    code: `// DynamoWorkflowStateStore.java
dynamoClient.putItem(PutItemRequest.builder()
  .tableName(TABLE_NAME)
  .item(toAttributeMap(workflowState))
  .conditionExpression(
    "attribute_not_exists(workflowId)"
  )
  .build());
  
// On retry/crash recovery — conditional update:
dynamoClient.updateItem(UpdateItemRequest.builder()
  .conditionExpression(
    "version = :expectedVersion"
  )
  .expressionAttributeValues(Map.of(
    ":expectedVersion", AttributeValue.fromN(
      String.valueOf(expectedVersion))
  ))
  .build());`,
    difficulties: [
      {
        problem: 'DynamoDB conditional write failures were being retried naively, causing duplicate step executions',
        solution: 'ConditionalCheckFailedException is now treated as "already exists" — not a transient error. The recovery path reads the existing item and resumes from its current step. Exponential backoff with jitter only applies to ProvisionedThroughputExceededException.',
      },
      {
        problem: 'Large workflow graphs (50+ steps) exceeded DynamoDB 400KB item size limit',
        solution: 'Step graph is now stored compressed (GZIP, Base64) in a Binary DynamoDB attribute. Measured 78% size reduction on typical graphs. Decompression happens lazily on read, not on every state update.',
      },
    ],
    outcome: 'Durable checkpoint created. System can now crash and restart without data loss.',
  },
  {
    id: 3,
    step: '04',
    name: 'Virtual Thread Execution',
    icon: Server,
    tagline: 'Steps execute in parallel on Java 21 Virtual Threads with retry and compensation.',
    duration: '71 ms',
    detail: `DagExecutor submits each ready step (steps with all dependencies completed) to a Virtual Thread pool (Executors.newVirtualThreadPerTaskExecutor()). Each step runs its registered StepHandler — which may call external services, perform computations, or invoke sub-workflows. Steps are independent; parallel branches execute concurrently. On completion, the step's output is persisted via a conditional DynamoDB update (version increment). If a step fails, the RetryPolicy (exponential backoff, configurable max attempts) governs re-attempts. If all retries exhaust, the CompensationExecutor runs registered rollback steps in reverse topological order.`,
    code: `// DagExecutor.java — Virtual Thread submission
try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
  List<Future<StepResult>> futures = readySteps.stream()
    .map(step -> pool.submit(() -> {
      var handler = registry.get(step.getType());
      return handler.execute(step, context);
    }))
    .toList();
  
  // Collect results — propagate first failure
  for (var f : futures) {
    results.add(f.get(30, TimeUnit.SECONDS));
  }
}`,
    difficulties: [
      {
        problem: 'Thread pool exhaustion under load — too many concurrent workflows starved each other',
        solution: 'Switched from a fixed-size thread pool to Virtual Threads (JEP 444). Virtual threads are cheap enough (~1KB stack) that each step gets its own thread. Carrier thread pool handles the actual scheduling transparently. Eliminated all timeout-related failures caused by pool starvation.',
      },
      {
        problem: 'Compensation (rollback) order was wrong — ran forward instead of reverse',
        solution: 'CompensationExecutor now takes the completed step list and reverses it using Collections.reverse() on the topological order, not the insertion order. Added an integration test that verifies rollback order by checking the sequence of compensation events published to Kafka.',
      },
    ],
    outcome: 'All DAG steps executed. Results collected. State updated in DynamoDB at each step boundary.',
  },
  {
    id: 4,
    step: '05',
    name: 'Kafka Event Publish',
    icon: Radio,
    tagline: 'Termination events are published exactly-once to Kafka using transactions.',
    duration: '18 ms',
    detail: `On workflow completion (all steps done or compensated), WorkflowEventPublisher publishes a WorkflowCompletedEvent to the workflows.completed Kafka topic. This is wrapped in a Kafka transaction (producer.beginTransaction() / commitTransaction()) that is coordinated with the final DynamoDB state update via a two-phase pattern: (1) DynamoDB conditional update to COMPLETED status, (2) Kafka transaction commit. If either fails, both are rolled back. The Kafka producer is configured with enable.idempotence=true and acks=all, ensuring no duplicate publishes even if the broker acknowledges late.`,
    code: `// KafkaWorkflowEventPublisher.java
producer.beginTransaction();
try {
  // 1. Final DynamoDB state transition
  stateStore.transitionToCompleted(workflowId);
  
  // 2. Publish termination event
  producer.send(new ProducerRecord<>(
    "workflows.completed",
    workflowId,
    WorkflowCompletedEvent.of(result)
  ));
  
  producer.commitTransaction();
} catch (Exception e) {
  producer.abortTransaction();
  throw new PublishFailureException(e);
}`,
    difficulties: [
      {
        problem: 'Kafka transactions added 40ms latency per workflow — unacceptable at 500 wf/sec',
        solution: 'Batched the Kafka producer with linger.ms=5 and batch.size=65536. At 500 wf/sec, batches naturally fill within the linger window, amortising transaction overhead across multiple workflows. Net latency impact dropped from 40ms to 18ms P99.',
      },
      {
        problem: 'Exactly-once guarantee broke when the broker restarted mid-transaction',
        solution: 'Set transactional.id to a stable per-orchestrator-instance ID (derived from hostname + port). On broker restart, the producer re-initialises the transaction coordinator using the same ID, which fences any zombie producers from the previous instance. Added a chaos test that kills the broker mid-transaction — confirmed 0 duplicates across 200 runs.',
      },
    ],
    outcome: 'Downstream consumers receive exactly-one completion event. Workflow lifecycle complete.',
  },
];

// ─── Components ──────────────────────────────────────────────────────────────

function StepNav({ current, setCurrent }: { current: number; setCurrent: (n: number) => void }) {
  return (
    <nav aria-label="Workflow simulation steps" className="flex items-stretch border border-[#27272A] bg-[#111117] mb-0 overflow-x-auto">
      {PHASES.map((p) => {
        const active = current === p.id;
        return (
          <button
            key={p.id}
            onClick={() => setCurrent(p.id)}
            aria-current={active ? 'step' : undefined}
            className={`flex-1 min-w-[120px] px-4 py-4 text-left border-r border-[#27272A] last:border-r-0 transition-colors duration-100 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#FF4D00] ${
              active ? 'bg-[#FF4D00]/8 border-t-2 border-t-[#FF4D00]' : 'hover:bg-[#27272A]/30 border-t-2 border-t-transparent'
            }`}
          >
            <div className="text-[10px] tracking-widest mb-1" style={{ fontFamily: 'var(--font-spacemono)', color: active ? '#FF4D00' : '#71717A' }}>
              {p.step}
            </div>
            <div className="text-xs font-bold" style={{ fontFamily: 'var(--font-spacemono)', color: active ? '#FAFAFA' : '#71717A' }}>
              {p.name}
            </div>
            <div className="text-[10px] mt-1" style={{ fontFamily: 'var(--font-spacemono)', color: '#71717A' }}>
              ~{p.duration}
            </div>
          </button>
        );
      })}
    </nav>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function SimulationPage() {
  const [current, setCurrent] = useState(0);
  const phase = PHASES[current];
  const Icon = phase.icon;

  return (
    <main className="min-h-screen bg-[#09090B] px-6 py-10 max-w-7xl mx-auto">

      {/* Header */}
      <div className="mb-10 border-b border-[#27272A] pb-10">
        <p className="text-[10px] tracking-[0.2em] uppercase text-[#FF4D00] mb-3" style={{ fontFamily: 'var(--font-spacemono)' }}>
          Process Simulation
        </p>
        <h1 className="text-5xl font-extrabold text-[#FAFAFA] mb-4 leading-none" style={{ fontFamily: 'var(--font-bricolage)' }}>
          Workflow lifecycle,<br />step by step.
        </h1>
        <p className="text-[#71717A] max-w-xl text-sm leading-relaxed" style={{ fontFamily: 'var(--font-manrope)' }}>
          A HELIOS workflow travels through five phases — from the initial HTTP trigger to a durably-published Kafka termination event.
          Select each phase to see the implementation detail, code, and the real engineering difficulties encountered and solved.
        </p>

        {/* Timeline progress */}
        <div className="flex items-center gap-0 mt-8">
          {PHASES.map((p, i) => (
            <div key={p.id} className="flex items-center flex-1">
              <button
                onClick={() => setCurrent(p.id)}
                className={`w-8 h-8 flex items-center justify-center text-[10px] font-bold border transition-colors ${
                  i < current ? 'border-[#22C55E] bg-[#22C55E]/10 text-[#22C55E]'
                    : i === current ? 'border-[#FF4D00] bg-[#FF4D00]/10 text-[#FF4D00]'
                      : 'border-[#27272A] bg-[#111117] text-[#71717A]'
                }`}
                style={{ fontFamily: 'var(--font-spacemono)' }}
                aria-label={`Go to phase ${p.step}: ${p.name}`}
              >
                {i < current ? '✓' : p.step}
              </button>
              {i < PHASES.length - 1 && (
                <div className={`flex-1 h-px ${i < current ? 'bg-[#22C55E]/40' : 'bg-[#27272A]'}`} />
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Step nav tabs */}
      <StepNav current={current} setCurrent={setCurrent} />

      {/* Phase detail */}
      <AnimatePresence mode="wait">
        <motion.div
          key={phase.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -4 }}
          transition={{ duration: 0.2, ease: 'easeOut' }}
          className="border border-t-0 border-[#27272A] bg-[#111117]"
        >
          {/* Phase header */}
          <div className="border-b border-[#27272A] px-6 py-5 flex items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-3 mb-2">
                <Icon size={18} className="text-[#FF4D00]" aria-hidden />
                <h2 className="text-2xl font-extrabold text-[#FAFAFA]" style={{ fontFamily: 'var(--font-bricolage)' }}>
                  {phase.name}
                </h2>
              </div>
              <p className="text-[#71717A] text-sm" style={{ fontFamily: 'var(--font-manrope)' }}>
                {phase.tagline}
              </p>
            </div>
            <div className="border border-[#27272A] px-3 py-2 shrink-0">
              <div className="text-[9px] text-[#71717A] tracking-widest" style={{ fontFamily: 'var(--font-spacemono)' }}>AVG DURATION</div>
              <div className="text-xl font-bold text-[#FF4D00]" style={{ fontFamily: 'var(--font-bricolage)' }}>{phase.duration}</div>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 divide-y lg:divide-y-0 lg:divide-x divide-[#27272A]">
            {/* Explanation */}
            <div className="p-6 space-y-6">
              <div>
                <h3 className="text-[10px] uppercase tracking-widest text-[#71717A] mb-3" style={{ fontFamily: 'var(--font-spacemono)' }}>
                  How it works
                </h3>
                <p className="text-[#FAFAFA]/80 text-sm leading-relaxed" style={{ fontFamily: 'var(--font-manrope)' }}>
                  {phase.detail}
                </p>
              </div>

              <div className="border-t border-[#27272A] pt-6">
                <h3 className="text-[10px] uppercase tracking-widest text-[#71717A] mb-4" style={{ fontFamily: 'var(--font-spacemono)' }}>
                  Outcome
                </h3>
                <div className="flex items-start gap-3 border border-[#22C55E]/30 bg-[#22C55E]/5 px-4 py-3">
                  <CheckCircle size={14} className="text-[#22C55E] mt-0.5 shrink-0" aria-hidden />
                  <span className="text-[#22C55E] text-xs" style={{ fontFamily: 'var(--font-spacemono)' }}>
                    {phase.outcome}
                  </span>
                </div>
              </div>
            </div>

            {/* Code + difficulties */}
            <div className="divide-y divide-[#27272A]">
              {/* Code */}
              <div className="p-6">
                <h3 className="text-[10px] uppercase tracking-widest text-[#71717A] mb-3" style={{ fontFamily: 'var(--font-spacemono)' }}>
                  Implementation
                </h3>
                <pre
                  className="text-[11px] text-[#FAFAFA]/80 overflow-x-auto leading-relaxed bg-[#09090B] border border-[#27272A] p-4"
                  style={{ fontFamily: 'var(--font-spacemono)' }}
                  aria-label="Code example"
                >
                  {phase.code}
                </pre>
              </div>

              {/* Difficulties */}
              <div className="p-6">
                <h3 className="text-[10px] uppercase tracking-widest text-[#EAB308] mb-4" style={{ fontFamily: 'var(--font-spacemono)' }}>
                  Difficulties & Solutions
                </h3>
                <div className="space-y-4">
                  {phase.difficulties.map((d, i) => (
                    <div key={i} className="border border-[#27272A]">
                      <div className="flex items-start gap-3 border-b border-[#27272A] px-4 py-3 bg-[#EAB308]/5">
                        <AlertTriangle size={12} className="text-[#EAB308] mt-0.5 shrink-0" aria-hidden />
                        <span className="text-[#EAB308] text-[11px]" style={{ fontFamily: 'var(--font-spacemono)' }}>
                          {d.problem}
                        </span>
                      </div>
                      <div className="flex items-start gap-3 px-4 py-3 bg-[#22C55E]/5">
                        <CheckCircle size={12} className="text-[#22C55E] mt-0.5 shrink-0" aria-hidden />
                        <span className="text-[#FAFAFA]/80 text-[11px] leading-relaxed" style={{ fontFamily: 'var(--font-manrope)' }}>
                          {d.solution}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </motion.div>
      </AnimatePresence>

      {/* Navigation buttons */}
      <div className="flex justify-between mt-4">
        <button
          onClick={() => setCurrent(Math.max(0, current - 1))}
          disabled={current === 0}
          className="border border-[#27272A] px-6 py-3 text-[11px] tracking-widest uppercase text-[#71717A] hover:text-[#FAFAFA] hover:border-[#71717A] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          style={{ fontFamily: 'var(--font-spacemono)' }}
        >
          ← Previous
        </button>
        <span className="text-[#71717A] text-[11px] self-center" style={{ fontFamily: 'var(--font-spacemono)' }}>
          {current + 1} / {PHASES.length}
        </span>
        <button
          onClick={() => setCurrent(Math.min(PHASES.length - 1, current + 1))}
          disabled={current === PHASES.length - 1}
          className="border border-[#FF4D00] px-6 py-3 text-[11px] tracking-widest uppercase text-[#FF4D00] hover:bg-[#FF4D00]/10 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          style={{ fontFamily: 'var(--font-spacemono)' }}
        >
          Next →
        </button>
      </div>
    </main>
  );
}
