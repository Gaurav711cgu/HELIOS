import { Database, Radio, Server, Cpu, GitBranch, Shield } from 'lucide-react';

const COMPONENTS = [
  {
    icon: Server,
    name: 'Spring Boot Orchestrator',
    role: 'Engine Core',
    description: 'The central coordination process. Handles REST ingress, DAG validation, virtual-thread execution scheduling, retry logic, and compensation. Runs on Java 21 with virtual threads for I/O-bound concurrency without thread pool exhaustion.',
    config: ['Virtual threads: JEP 444', 'Max concurrent workflows: 1,000', 'Polling interval: 100ms', 'DB connection pool: 20'],
    port: '8080',
    color: '#FF4D00',
  },
  {
    icon: Database,
    name: 'Amazon DynamoDB',
    role: 'State Store',
    description: 'Primary persistence layer for workflow state. Conditional writes (attribute_not_exists) enforce exactly-once creation. Version-based optimistic locking enforces exactly-once step transitions. Supports point-in-time recovery and on-demand capacity.',
    config: ['Conditional expressions: enabled', 'Table: helios-workflows', 'GSI: by-tenant-status', 'TTL: 90 days'],
    port: '8000 (local)',
    color: '#3E6DB4',
  },
  {
    icon: Radio,
    name: 'Apache Kafka',
    role: 'Event Bus',
    description: 'Durable event streaming for workflow lifecycle events. Kafka transactions wrap the DynamoDB state transition and event publish atomically. Exactly-once producer semantics (enable.idempotence=true, acks=all) prevent duplicate events.',
    config: ['enable.idempotence: true', 'acks: all', 'linger.ms: 5', 'batch.size: 65536'],
    port: '9092',
    color: '#22C55E',
  },
  {
    icon: Shield,
    name: 'Redis',
    role: 'Quota Guard',
    description: 'Per-tenant rate limiting enforced via Redis INCR + EXPIRE in a Lua script for atomicity. Each tenant gets a configurable rolling 1-second window. Redis is not on the critical execution path — only on the ingress path.',
    config: ['Pattern: INCR + EXPIRE', 'Window: 1 second sliding', 'Default quota: 100 rps', 'Eviction policy: allkeys-lru'],
    port: '6379',
    color: '#EAB308',
  },
  {
    icon: Cpu,
    name: 'Worker SDK / gRPC',
    role: 'External Workers',
    description: 'External step execution via gRPC. Workers poll the orchestrator for available tasks (Poll RPC), execute, then report completion or failure (Complete RPC) with heartbeats (Heartbeat RPC) to prevent false timeouts. Implemented with io.grpc and Protocol Buffers.',
    config: ['Protocol: gRPC / proto3', 'Port: 9090', 'Heartbeat: 10s interval', 'Task timeout: 60s'],
    port: '9090',
    color: '#71717A',
  },
  {
    icon: GitBranch,
    name: 'AI DAG Synthesis',
    role: 'NL → Workflow',
    description: 'Natural-language workflow descriptions are converted to DAG JSON via the Anthropic Claude API. Strict JSON-only mode enforced — markdown fences stripped, malformed output rejected. Validated against DagDefinition schema before any persistence.',
    config: ['Model: claude-3-haiku', 'Validation: strict JSON', 'Fallback: 422 on invalid', 'Key: ANTHROPIC_API_KEY env'],
    port: 'HTTPS external',
    color: '#71717A',
  },
];

const FLOW = [
  { from: 'REST Client', to: 'Spring Boot', label: 'HTTP POST trigger' },
  { from: 'Spring Boot', to: 'DynamoDB', label: 'Conditional state write' },
  { from: 'Spring Boot', to: 'Redis', label: 'Quota check (Lua)' },
  { from: 'Spring Boot', to: 'Virtual Threads', label: 'Step dispatch' },
  { from: 'Virtual Threads', to: 'DynamoDB', label: 'Step state update' },
  { from: 'Virtual Threads', to: 'Kafka', label: 'Termination event (TX)' },
  { from: 'External Worker', to: 'gRPC Server', label: 'Poll / Complete / Heartbeat' },
];

export default function ArchitecturePage() {
  return (
    <main className="min-h-screen bg-[#09090B] px-6 py-10 max-w-7xl mx-auto space-y-16">

      {/* Header */}
      <div className="border-b border-[#27272A] pb-10">
        <p className="text-[10px] tracking-[0.2em] uppercase text-[#FF4D00] mb-3" style={{ fontFamily: 'var(--font-spacemono)' }}>
          System Architecture
        </p>
        <h1 className="text-5xl font-extrabold text-[#FAFAFA] mb-4 leading-none" style={{ fontFamily: 'var(--font-bricolage)' }}>
          Java orchestration<br />backed by real infra.
        </h1>
        <p className="text-[#71717A] max-w-xl text-sm leading-relaxed" style={{ fontFamily: 'var(--font-manrope)' }}>
          The repository separates engine logic from adapters. Production connects Spring Boot to DynamoDB, Kafka, and Redis
          through explicit infrastructure implementations — no in-memory fakes in the execution path.
        </p>
      </div>

      {/* ASCII architecture diagram */}
      <section aria-label="Architecture data flow diagram">
        <p className="text-[10px] uppercase tracking-widest text-[#71717A] mb-4" style={{ fontFamily: 'var(--font-spacemono)' }}>
          Data Flow
        </p>
        <div className="border border-[#27272A] bg-[#111117] p-6 overflow-x-auto">
          <pre className="text-[11px] leading-relaxed text-[#FAFAFA]/70" style={{ fontFamily: 'var(--font-spacemono)' }}>
{`
  REST Client ─────────────────────────────────────────────────────────────
       │                                                                    
       │ POST /trigger                                                      
       ▼                                                                    
  ┌─────────────────────────────────────────────────────────────────┐      
  │            Spring Boot Orchestrator  :8080                      │      
  │                                                                 │      
  │  WorkflowController                                             │      
  │       │                                                         │      
  │       ├── QuotaFilter ──────────────────────────► Redis :6379   │      
  │       │                                                         │      
  │       ├── DagValidator (cycle check + handler check)            │      
  │       │                                                         │      
  │       ├── WorkflowStateStore ──────────────────► DynamoDB :8000 │      
  │       │         (conditional put_item)                          │      
  │       │                                                         │      
  │       └── DagExecutor                                           │      
  │                 │                                               │      
  │    ┌────────────┴────────────┐                                  │      
  │    │ Virtual Thread Pool     │                                  │      
  │    │  step-1  step-2  step-3 │ ──────────────────► DynamoDB     │      
  │    └────────────┬────────────┘    (version-locked updates)      │      
  │                 │                                               │      
  │       WorkflowEventPublisher ───────────────────► Kafka :9092   │      
  │                 (Kafka transaction)                             │      
  └─────────────────────────────────────────────────────────────────┘      
                    │                                                       
                    │ gRPC :9090 (optional external workers)                
                    ▼                                                       
            External Worker SDK                                            
            (Poll / Execute / Complete / Heartbeat)                        
`}
          </pre>
        </div>
      </section>

      {/* Component grid */}
      <section aria-label="Architecture components">
        <p className="text-[10px] uppercase tracking-widest text-[#71717A] mb-6" style={{ fontFamily: 'var(--font-spacemono)' }}>
          Components
        </p>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-px bg-[#27272A]">
          {COMPONENTS.map((c) => {
            const Icon = c.icon;
            return (
              <div key={c.name} className="bg-[#111117] p-6 flex flex-col gap-4" style={{ borderTop: `2px solid ${c.color}` }}>
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-3">
                    <Icon size={18} style={{ color: c.color }} aria-hidden />
                    <div>
                      <div className="text-sm font-bold text-[#FAFAFA]" style={{ fontFamily: 'var(--font-spacemono)' }}>{c.name}</div>
                      <div className="text-[10px] tracking-widest mt-0.5" style={{ fontFamily: 'var(--font-spacemono)', color: c.color }}>{c.role}</div>
                    </div>
                  </div>
                  <span className="text-[9px] border border-[#27272A] px-2 py-1 text-[#71717A]" style={{ fontFamily: 'var(--font-spacemono)' }}>
                    :{c.port}
                  </span>
                </div>
                <p className="text-[#71717A] text-xs leading-relaxed" style={{ fontFamily: 'var(--font-manrope)' }}>
                  {c.description}
                </p>
                <div className="border-t border-[#27272A] pt-4">
                  <div className="text-[10px] uppercase tracking-widest text-[#71717A] mb-2" style={{ fontFamily: 'var(--font-spacemono)' }}>Config</div>
                  <ul className="space-y-1">
                    {c.config.map(cfg => (
                      <li key={cfg} className="flex items-center gap-2 text-[10px] text-[#FAFAFA]/70" style={{ fontFamily: 'var(--font-spacemono)' }}>
                        <span style={{ color: c.color }}>›</span> {cfg}
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            );
          })}
        </div>
      </section>

      {/* Data flow table */}
      <section>
        <p className="text-[10px] uppercase tracking-widest text-[#71717A] mb-4" style={{ fontFamily: 'var(--font-spacemono)' }}>
          Interaction Matrix
        </p>
        <div className="border border-[#27272A] bg-[#111117] overflow-x-auto">
          <table className="w-full text-xs" style={{ fontFamily: 'var(--font-spacemono)' }}>
            <thead>
              <tr className="border-b border-[#27272A]">
                {['From', 'To', 'Protocol'].map(h => (
                  <th key={h} className="text-left px-4 py-3 text-[10px] uppercase tracking-widest text-[#71717A]">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {FLOW.map((row, i) => (
                <tr key={i} className="border-b border-[#27272A] hover:bg-[#27272A]/20 transition-colors">
                  <td className="px-4 py-3 text-[#FAFAFA]">{row.from}</td>
                  <td className="px-4 py-3 text-[#FAFAFA]">{row.to}</td>
                  <td className="px-4 py-3 text-[#71717A]">{row.label}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

    </main>
  );
}
