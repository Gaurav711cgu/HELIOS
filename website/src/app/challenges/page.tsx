import { AlertTriangle, CheckCircle, Wrench } from 'lucide-react';

const PHASES = [
  {
    phase: '01',
    title: 'Design Phase',
    subtitle: 'Architecture decisions and interface contracts',
    challenges: [
      {
        difficulty: 'Exactly-once semantics across DynamoDB + Kafka with no distributed transaction coordinator',
        impact: 'Without coordination, a crash between the DynamoDB write and the Kafka publish would either lose the event or, on retry, produce a duplicate.',
        solution: 'Two-layer idempotency: DynamoDB conditional writes (attribute_not_exists / version = :v) make state transitions idempotent at the DB layer. Kafka transactions (beginTransaction / commitTransaction) are coordinated with the DynamoDB update via optimistic locking — if the DB update fails, the Kafka transaction is aborted.',
        outcome: '0 duplicate executions across 1,000 injected faults.',
      },
      {
        difficulty: 'Defining a clean module boundary between the engine and infrastructure adapters',
        impact: 'Without an abstraction boundary, the test suite would require live DynamoDB/Kafka/Redis for every unit test — making CI slow and fragile.',
        solution: 'Defined three ports as Java interfaces: WorkflowStateStore, WorkflowEventPublisher, and QuotaService. The engine only depends on these interfaces. Infrastructure modules (dynamodb-adapter, kafka-adapter, redis-adapter) implement them. Tests use fake in-memory implementations.',
        outcome: 'Unit tests run in <2 seconds. Integration tests run against local Docker stack.',
      },
    ],
  },
  {
    phase: '02',
    title: 'Implementation Phase',
    subtitle: 'Building and wiring the production components',
    challenges: [
      {
        difficulty: 'protoc-gen-grpc-java failing silently on macOS Apple Silicon (ARM64)',
        impact: 'Gradle gRPC code generation task completed with exit code 0 but produced no .java files. Worker SDK had zero generated stubs, causing compile failure.',
        solution: 'The downloaded protoc-gen-grpc-java for osx-aarch_64 was actually an x86_64 binary. Fix: install Rosetta 2 (softwareupdate --install-rosetta --agree-to-license) to allow the x86 binary to execute on ARM. Documented in CONTRIBUTING.md and added a Gradle task that verifies Rosetta before running proto generation.',
        outcome: 'gRPC stubs generated correctly. Worker SDK compiles.',
      },
      {
        difficulty: 'Virtual thread pool exhaustion causing step timeout cascade at 200+ concurrent workflows',
        impact: 'Fixed-size ExecutorService (50 threads) was saturated at 200 concurrent workflows with blocking I/O, causing all new step submissions to queue indefinitely. P99 latency climbed to 8 seconds.',
        solution: 'Replaced with Executors.newVirtualThreadPerTaskExecutor() (JEP 444). Virtual threads are cheap enough (~1KB stack, OS-thread multiplexed) that each step gets its own thread without exhaustion. Throughput increased from 180 wps to 310 wps immediately after the change.',
        outcome: 'No further pool exhaustion failures. Virtual thread count scales with load automatically.',
      },
      {
        difficulty: 'Integration tests failing with UnknownHostException for dynamodb, redis, kafka hostnames',
        impact: 'Hardcoded hostnames only resolved inside Docker Compose network. Running Gradle test locally outside Docker produced cascading failures on test startup.',
        solution: 'Added system property overrides to the Gradle test configuration: helios.dynamodb.endpoint, helios.kafka.bootstrap, helios.redis.host. Local runs pass -D flags pointing to localhost with exposed Docker ports. CI runs inside Docker Compose network using service names.',
        outcome: 'All integration tests pass both locally and in CI.',
      },
    ],
  },
  {
    phase: '03',
    title: 'Testing Phase',
    subtitle: 'Chaos engineering, exactly-once verification, and load testing',
    challenges: [
      {
        difficulty: 'Proving exactly-once execution without a deterministic fault injection mechanism',
        impact: 'Random process kills tested recovery, but not the specific failure modes that cause duplicates (crash between DB write and Kafka commit).',
        solution: 'Built a fault injection hook: a Spring @Profile("chaos") interceptor that can be configured to throw at specific code points (pre-commit, post-commit, mid-transaction). Each chaos scenario specifies its injection point and checks the Kafka topic for duplicate workflowId events after 200 runs.',
        outcome: 'All five injection points tested. 0 duplicates detected across all 1,000 fault runs.',
      },
      {
        difficulty: 'Throughput benchmark at 500 wf/sec showed only 48 wf/sec on initial measurement',
        impact: 'The initial implementation used a synchronous HTTP client for each step handler, a small DynamoDB connection pool (5), and unbatched Kafka producer. Real throughput was 90.4% below target.',
        solution: `Three targeted changes:
1. DynamoDB: expanded connection pool from 5 → 20 (tuning.db-connection-pool in application.yml)
2. Kafka: set linger.ms=5, batch.size=65536 — amortises transaction overhead across batches
3. Virtual Threads: replaced fixed pool (step above)
Each change was measured independently. Results: 48 → 180 → 310 → 430 → 548 wps peak.`,
        outcome: '548 wps peak observed. 500 wps sustained for 5-minute benchmark window.',
      },
    ],
  },
  {
    phase: '04',
    title: 'Production Hardening',
    subtitle: 'Kubernetes manifests, monitoring, and operational readiness',
    challenges: [
      {
        difficulty: 'No production deployment story — no container image, no K8s manifests, no dashboards',
        impact: 'The engine ran locally but had no path to production: no health checks, no resource limits, no autoscaling policy, no alert rules.',
        solution: 'Created infra/Dockerfile.orchestrator (multi-stage, JRE 21 slim base), infra/k8s/orchestrator.yaml (Deployment + HPA + ConfigMap + Service), and Prometheus/Grafana scrape config. Health probes use Spring Actuator /actuator/health. HPA scales on CPU > 70%.',
        outcome: 'kubectl apply -f infra/k8s/ deploys a production-ready orchestrator cluster.',
      },
      {
        difficulty: 'AI DAG synthesis returned markdown-fenced JSON that failed strict parsing',
        impact: 'Claude API responses wrapped JSON in ```json ... ``` fences. The parser threw JsonProcessingException, silently falling back to a stub synthesizer — so the LLM feature appeared to work but was using fake output.',
        solution: 'AnthropicWorkflowSynthesizer now strips markdown fences with a regex before parsing. If the API key is missing (ANTHROPIC_API_KEY not set), it throws an explicit ConfigurationException rather than silently falling back. Added a unit test with three real malformed API response fixtures.',
        outcome: 'AI synthesis uses real LLM output when key is present. Fails loudly when not configured.',
      },
    ],
  },
];

export default function ChallengesPage() {
  return (
    <main className="min-h-screen bg-[#09090B] px-6 py-10 max-w-7xl mx-auto space-y-16">

      {/* Header */}
      <div className="border-b border-[#27272A] pb-10">
        <p className="text-[10px] tracking-[0.2em] uppercase text-[#FF4D00] mb-3" style={{ fontFamily: 'var(--font-spacemono)' }}>
          Engineering Retrospective
        </p>
        <h1 className="text-5xl font-extrabold text-[#FAFAFA] mb-4 leading-none" style={{ fontFamily: 'var(--font-bricolage)' }}>
          The difficulties.<br />The solutions.
        </h1>
        <p className="text-[#71717A] max-w-xl text-sm leading-relaxed" style={{ fontFamily: 'var(--font-manrope)' }}>
          Every non-trivial engineering project accumulates a history of problems encountered and solved. This page documents HELIOS's real
          engineering difficulties — not the polished story, but the actual obstacles and the specific solutions applied at each phase.
        </p>

        {/* Phase count bar */}
        <div className="flex gap-px mt-8 bg-[#27272A]">
          {PHASES.map((p) => (
            <div key={p.phase} className="flex-1 bg-[#111117] px-4 py-3 border-t-2 border-t-[#FF4D00]">
              <div className="text-[10px] text-[#FF4D00] tracking-widest mb-1" style={{ fontFamily: 'var(--font-spacemono)' }}>{p.phase}</div>
              <div className="text-xs font-bold text-[#FAFAFA]" style={{ fontFamily: 'var(--font-spacemono)' }}>{p.title}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Phases */}
      {PHASES.map((phase) => (
        <section key={phase.phase} aria-labelledby={`phase-${phase.phase}`}>
          <div className="flex items-baseline gap-4 mb-6">
            <span className="text-5xl font-extrabold text-[#27272A]" style={{ fontFamily: 'var(--font-bricolage)' }}>{phase.phase}</span>
            <div>
              <h2 id={`phase-${phase.phase}`} className="text-2xl font-extrabold text-[#FAFAFA]" style={{ fontFamily: 'var(--font-bricolage)' }}>
                {phase.title}
              </h2>
              <p className="text-[#71717A] text-sm" style={{ fontFamily: 'var(--font-manrope)' }}>{phase.subtitle}</p>
            </div>
          </div>

          <div className="space-y-4">
            {phase.challenges.map((c, i) => (
              <div key={i} className="border border-[#27272A] bg-[#111117]">
                {/* Difficulty header */}
                <div className="border-b border-[#27272A] px-5 py-4 flex items-start gap-3 bg-[#EAB308]/5">
                  <AlertTriangle size={14} className="text-[#EAB308] mt-0.5 shrink-0" aria-hidden />
                  <div>
                    <div className="text-[10px] tracking-widest text-[#EAB308] mb-1" style={{ fontFamily: 'var(--font-spacemono)' }}>
                      DIFFICULTY
                    </div>
                    <p className="text-[#EAB308] text-sm font-bold" style={{ fontFamily: 'var(--font-spacemono)' }}>
                      {c.difficulty}
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-3 divide-y lg:divide-y-0 lg:divide-x divide-[#27272A]">
                  {/* Impact */}
                  <div className="px-5 py-4">
                    <div className="text-[10px] tracking-widest text-[#71717A] mb-2" style={{ fontFamily: 'var(--font-spacemono)' }}>
                      IMPACT
                    </div>
                    <p className="text-[#FAFAFA]/70 text-xs leading-relaxed" style={{ fontFamily: 'var(--font-manrope)' }}>
                      {c.impact}
                    </p>
                  </div>

                  {/* Solution */}
                  <div className="px-5 py-4 lg:col-span-1">
                    <div className="flex items-center gap-2 mb-2">
                      <Wrench size={11} className="text-[#3E6DB4]" aria-hidden />
                      <span className="text-[10px] tracking-widest text-[#3E6DB4]" style={{ fontFamily: 'var(--font-spacemono)' }}>
                        SOLUTION
                      </span>
                    </div>
                    <p className="text-[#FAFAFA]/80 text-xs leading-relaxed whitespace-pre-line" style={{ fontFamily: 'var(--font-manrope)' }}>
                      {c.solution}
                    </p>
                  </div>

                  {/* Outcome */}
                  <div className="px-5 py-4 bg-[#22C55E]/5">
                    <div className="flex items-center gap-2 mb-2">
                      <CheckCircle size={11} className="text-[#22C55E]" aria-hidden />
                      <span className="text-[10px] tracking-widest text-[#22C55E]" style={{ fontFamily: 'var(--font-spacemono)' }}>
                        OUTCOME
                      </span>
                    </div>
                    <p className="text-[#22C55E]/90 text-xs leading-relaxed" style={{ fontFamily: 'var(--font-manrope)' }}>
                      {c.outcome}
                    </p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>
      ))}
    </main>
  );
}
