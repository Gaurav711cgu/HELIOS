import { CheckCircle, X, Minus } from 'lucide-react';

type CellValue = boolean | 'partial' | string;

const FEATURES: Array<{ category: string; rows: Array<{ feature: string; desc: string; helios: CellValue; temporal: CellValue; conductor: CellValue; airflow: CellValue }> }> = [
  {
    category: 'Execution Model',
    rows: [
      { feature: 'Exactly-once execution', desc: 'Guaranteed no duplicate step execution on crash/retry', helios: true, temporal: true, conductor: 'partial', airflow: false },
      { feature: 'Durable state persistence', desc: 'Workflow state survives orchestrator restarts', helios: true, temporal: true, conductor: true, airflow: true },
      { feature: 'Virtual thread concurrency', desc: 'JVM virtual threads (JEP 444) for step parallelism', helios: true, temporal: false, conductor: false, airflow: false },
      { feature: 'External worker protocol', desc: 'Workers poll tasks over gRPC', helios: true, temporal: true, conductor: true, airflow: false },
      { feature: 'Compensation / rollback', desc: 'Automatic reverse-order compensation on failure', helios: true, temporal: true, conductor: 'partial', airflow: false },
    ],
  },
  {
    category: 'Infrastructure',
    rows: [
      { feature: 'State store', desc: 'Persistence backend', helios: 'DynamoDB', temporal: 'PostgreSQL / Cassandra', conductor: 'Redis / RDS', airflow: 'PostgreSQL' },
      { feature: 'Event bus', desc: 'Workflow event streaming', helios: 'Kafka (TX)', temporal: 'Built-in', conductor: 'Redis Streams', airflow: 'Celery / RabbitMQ' },
      { feature: 'Java-native', desc: 'First-class Java SDK without adapter overhead', helios: true, temporal: true, conductor: true, airflow: false },
      { feature: 'Spring Boot integration', desc: 'Native Spring Boot auto-configuration', helios: true, temporal: 'partial', conductor: 'partial', airflow: false },
      { feature: 'Kubernetes-ready', desc: 'Published Helm charts or K8s manifests', helios: true, temporal: true, conductor: true, airflow: true },
    ],
  },
  {
    category: 'Performance',
    rows: [
      { feature: 'Throughput target', desc: 'Sustained workflows/sec', helios: '500+/sec', temporal: '10k+/sec', conductor: '1k+/sec', airflow: '<100/sec' },
      { feature: 'P99 latency', desc: 'End-to-end trigger to completion', helios: '141 ms', temporal: '<100 ms', conductor: '~200 ms', airflow: '>500 ms' },
      { feature: 'Crash recovery P99', desc: 'Time to resume after orchestrator crash', helios: '3.2 sec', temporal: '<5 sec', conductor: '~10 sec', airflow: 'Manual' },
      { feature: 'Exactly-once proof', desc: 'Documented chaos test with 0 dupes', helios: true, temporal: true, conductor: false, airflow: false },
    ],
  },
  {
    category: 'Developer Experience',
    rows: [
      { feature: 'NL → DAG synthesis', desc: 'Natural language to workflow via LLM', helios: true, temporal: false, conductor: false, airflow: false },
      { feature: 'Tenant quota enforcement', desc: 'Per-tenant rate limiting at ingress', helios: true, temporal: 'partial', conductor: false, airflow: false },
      { feature: 'Local dev stack', desc: 'Docker Compose for full local environment', helios: true, temporal: true, conductor: true, airflow: true },
      { feature: 'Open source', desc: 'Apache 2 / MIT license', helios: 'MIT', temporal: 'MIT', conductor: 'Apache 2', airflow: 'Apache 2' },
      { feature: 'Commit-backed benchmarks', desc: 'Results stored in repo, not just claimed', helios: true, temporal: false, conductor: false, airflow: false },
    ],
  },
];

function Cell({ value }: { value: CellValue }) {
  if (value === true)
    return <CheckCircle size={14} className="text-[#22C55E] mx-auto" aria-label="Yes" />;
  if (value === false)
    return <X size={14} className="text-[#71717A] mx-auto" aria-label="No" />;
  if (value === 'partial')
    return <Minus size={14} className="text-[#EAB308] mx-auto" aria-label="Partial" />;
  return <span className="text-[#FAFAFA] text-[10px]" style={{ fontFamily: 'var(--font-spacemono)' }}>{value}</span>;
}

const ENGINES = [
  { name: 'HELIOS', sub: 'This project', highlight: true },
  { name: 'Temporal', sub: 'Temporal.io', highlight: false },
  { name: 'Conductor', sub: 'Netflix OSS', highlight: false },
  { name: 'Airflow', sub: 'Apache', highlight: false },
];

const SUMMARY = [
  { label: 'Exact. Once Proven', helios: '✓', temporal: '✓', conductor: '~', airflow: '✗' },
  { label: 'Java Native', helios: '✓', temporal: '✓', conductor: '✓', airflow: '✗' },
  { label: 'NL Synthesis', helios: '✓', temporal: '✗', conductor: '✗', airflow: '✗' },
  { label: 'Commit Benchmarks', helios: '✓', temporal: '✗', conductor: '✗', airflow: '✗' },
];

export default function ComparePage() {
  return (
    <main className="min-h-screen bg-[#09090B] px-6 py-10 max-w-7xl mx-auto space-y-16">

      {/* Header */}
      <div className="border-b border-[#27272A] pb-10">
        <p className="text-[10px] tracking-[0.2em] uppercase text-[#FF4D00] mb-3" style={{ fontFamily: 'var(--font-spacemono)' }}>
          Competitive Comparison
        </p>
        <h1 className="text-5xl font-extrabold text-[#FAFAFA] mb-4 leading-none" style={{ fontFamily: 'var(--font-bricolage)' }}>
          HELIOS vs<br />the field.
        </h1>
        <p className="text-[#71717A] max-w-xl text-sm leading-relaxed" style={{ fontFamily: 'var(--font-manrope)' }}>
          Compared against three mature, widely-deployed workflow orchestration systems: Temporal, Netflix Conductor, and Apache Airflow.
          HELIOS is a newer system — it outperforms in some dimensions (NL synthesis, exactly-once proof, commit-backed benchmarks)
          and is appropriately behind in others (raw throughput ceiling, ecosystem maturity).
        </p>

        {/* Summary pills */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-px mt-8 bg-[#27272A]">
          {SUMMARY.map(({ label, ...rest }) => (
            <div key={label} className="bg-[#111117] px-4 py-4">
              <div className="text-[10px] text-[#71717A] mb-3" style={{ fontFamily: 'var(--font-spacemono)' }}>{label}</div>
              <div className="grid grid-cols-4 gap-1 text-[10px] text-center" style={{ fontFamily: 'var(--font-spacemono)' }}>
                {[
                  { name: 'H', val: rest.helios, color: '#FF4D00' },
                  { name: 'T', val: rest.temporal, color: '#71717A' },
                  { name: 'C', val: rest.conductor, color: '#71717A' },
                  { name: 'A', val: rest.airflow, color: '#71717A' },
                ].map(({ name, val, color }) => (
                  <div key={name}>
                    <div style={{ color }} className="font-bold">{name}</div>
                    <div className={val === '✓' ? 'text-[#22C55E]' : val === '✗' ? 'text-[#27272A]' : 'text-[#EAB308]'}>{val}</div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Comparison table */}
      <section aria-label="Feature comparison table">
        <div className="border border-[#27272A] bg-[#111117] overflow-x-auto">
          {/* Table header */}
          <div className="grid border-b border-[#27272A]" style={{ gridTemplateColumns: '280px repeat(4, 1fr)' }}>
            <div className="px-4 py-4" />
            {ENGINES.map((e) => (
              <div
                key={e.name}
                className={`px-4 py-4 text-center border-l border-[#27272A] ${e.highlight ? 'border-t-2 border-t-[#FF4D00] bg-[#FF4D00]/5' : ''}`}
              >
                <div className="font-bold text-sm" style={{ fontFamily: 'var(--font-spacemono)', color: e.highlight ? '#FF4D00' : '#FAFAFA' }}>
                  {e.name}
                </div>
                <div className="text-[10px] text-[#71717A] mt-0.5" style={{ fontFamily: 'var(--font-spacemono)' }}>{e.sub}</div>
              </div>
            ))}
          </div>

          {/* Categories + rows */}
          {FEATURES.map((section) => (
            <div key={section.category}>
              <div className="px-4 py-2 bg-[#27272A]/40 border-b border-[#27272A]">
                <span className="text-[10px] uppercase tracking-widest text-[#71717A]" style={{ fontFamily: 'var(--font-spacemono)' }}>
                  {section.category}
                </span>
              </div>
              {section.rows.map((row, i) => (
                <div
                  key={i}
                  className="grid border-b border-[#27272A] hover:bg-[#27272A]/20 transition-colors"
                  style={{ gridTemplateColumns: '280px repeat(4, 1fr)' }}
                >
                  <div className="px-4 py-3">
                    <div className="text-xs text-[#FAFAFA] font-bold mb-0.5" style={{ fontFamily: 'var(--font-spacemono)' }}>
                      {row.feature}
                    </div>
                    <div className="text-[10px] text-[#71717A]" style={{ fontFamily: 'var(--font-manrope)' }}>
                      {row.desc}
                    </div>
                  </div>
                  {[row.helios, row.temporal, row.conductor, row.airflow].map((val, j) => (
                    <div
                      key={j}
                      className={`px-4 py-3 flex items-center justify-center border-l border-[#27272A] ${j === 0 ? 'bg-[#FF4D00]/5' : ''}`}
                    >
                      <Cell value={val} />
                    </div>
                  ))}
                </div>
              ))}
            </div>
          ))}
        </div>

        {/* Legend */}
        <div className="flex gap-6 mt-3 text-[10px] text-[#71717A]" style={{ fontFamily: 'var(--font-spacemono)' }}>
          <span className="flex items-center gap-1.5"><CheckCircle size={11} className="text-[#22C55E]" /> Yes / Supported</span>
          <span className="flex items-center gap-1.5"><Minus size={11} className="text-[#EAB308]" /> Partial / Limited</span>
          <span className="flex items-center gap-1.5"><X size={11} className="text-[#71717A]" /> Not supported</span>
        </div>
      </section>

      {/* Honest assessment */}
      <section className="border border-[#27272A] bg-[#111117] p-8">
        <h2 className="text-xl font-extrabold text-[#FAFAFA] mb-6" style={{ fontFamily: 'var(--font-bricolage)' }}>
          Honest assessment
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          <div>
            <h3 className="text-[10px] uppercase tracking-widest text-[#22C55E] mb-4" style={{ fontFamily: 'var(--font-spacemono)' }}>
              Where HELIOS leads
            </h3>
            <ul className="space-y-3 text-sm text-[#FAFAFA]/80" style={{ fontFamily: 'var(--font-manrope)', lineHeight: 1.7 }}>
              <li className="flex gap-3"><CheckCircle size={14} className="text-[#22C55E] mt-0.5 shrink-0" /><span>Natural-language DAG synthesis via LLM — no other listed system has this feature at all.</span></li>
              <li className="flex gap-3"><CheckCircle size={14} className="text-[#22C55E] mt-0.5 shrink-0" /><span>Commit-backed benchmark artifacts — results stored in <code className="text-[#FF4D00] text-xs" style={{ fontFamily: 'var(--font-spacemono)' }}>benchmark-results/</code>, not just claimed in a README.</span></li>
              <li className="flex gap-3"><CheckCircle size={14} className="text-[#22C55E] mt-0.5 shrink-0" /><span>Java 21 virtual threads — HELIOS is the only engine here that uses JEP 444 as a primary concurrency model.</span></li>
              <li className="flex gap-3"><CheckCircle size={14} className="text-[#22C55E] mt-0.5 shrink-0" /><span>DynamoDB-native state store — purpose-built for serverless/AWS-first deployments without a relational database dependency.</span></li>
            </ul>
          </div>
          <div>
            <h3 className="text-[10px] uppercase tracking-widest text-[#EAB308] mb-4" style={{ fontFamily: 'var(--font-spacemono)' }}>
              Where HELIOS is behind
            </h3>
            <ul className="space-y-3 text-sm text-[#FAFAFA]/80" style={{ fontFamily: 'var(--font-manrope)', lineHeight: 1.7 }}>
              <li className="flex gap-3"><Minus size={14} className="text-[#EAB308] mt-0.5 shrink-0" /><span>Raw throughput ceiling: Temporal handles 10k+ wps in production at scale. HELIOS targets 500 wps with a single node — multi-node orchestration is not yet built.</span></li>
              <li className="flex gap-3"><Minus size={14} className="text-[#EAB308] mt-0.5 shrink-0" /><span>Ecosystem maturity: Temporal has SDKs in 6+ languages. HELIOS currently provides Java and a minimal gRPC worker contract.</span></li>
              <li className="flex gap-3"><Minus size={14} className="text-[#EAB308] mt-0.5 shrink-0" /><span>Production track record: Temporal and Airflow have years of production deployments. HELIOS has a rigorous test rig but no production traffic yet.</span></li>
            </ul>
          </div>
        </div>
      </section>
    </main>
  );
}
