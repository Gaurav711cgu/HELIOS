import { TrendingUp, Activity, ShieldCheck, Zap } from 'lucide-react';

const METRICS = [
  {
    title: 'Unit Economics & Margin',
    tech: 'Java 21 Virtual Threads + DynamoDB',
    metric: 'Gross Margin > 90%',
    desc: 'By achieving 548 workflows/sec on a single JVM node, infrastructure COGS are driven to the floor. Traditional orchestrators require heavy K8s clusters. HELIOS runs lean, translating directly to massive gross margins and faster CAC Payback (< 12 months).',
    icon: TrendingUp,
    color: '#22C55E'
  },
  {
    title: 'Retention & Churn',
    tech: 'Exactly-Once Execution Guarantees',
    metric: 'NDR > 120%',
    desc: 'Enterprise churn is often driven by reliability failures (e.g., duplicate billing, dropped transactions). Our chaos-tested exactly-once state machine prevents this class of errors entirely, building trust and driving Net Dollar Retention (NDR) into best-in-class territory.',
    icon: ShieldCheck,
    color: '#3B82F6'
  },
  {
    title: 'Sales Efficiency',
    tech: 'Natural Language DAG Synthesis',
    metric: 'Shortened Sales Cycle',
    desc: 'B2B infrastructure usually has a 3-6 month sales cycle due to integration friction. HELIOS allows developers to generate complex workflow DAGs via LLM prompts. This radically lowers the barrier to entry, improving the Win Rate and shortening the trial-to-paid conversion cycle.',
    icon: Zap,
    color: '#F59E0B'
  },
  {
    title: 'Growth Readiness',
    tech: 'Horizontal Scalability',
    metric: 'Rule of 40 Pipeline',
    desc: 'With Kafka as the event bus, the engine scales horizontally out of the box. This architectural readiness allows the business to focus capital on S&M rather than R&D rewrites as user demand grows, ensuring we can maintain 100%+ YoY growth without sacrificing margins.',
    icon: Activity,
    color: '#8B5CF6'
  }
];

export default function InvestorsPage() {
  return (
    <main className="min-h-screen bg-[#09090B] px-6 py-10 max-w-7xl mx-auto space-y-16">
      {/* Header */}
      <div className="border-b border-[#27272A] pb-10">
        <p className="text-[10px] tracking-[0.2em] uppercase text-[#22C55E] mb-3" style={{ fontFamily: 'var(--font-spacemono)' }}>
          Business Case & Startup Metrics
        </p>
        <h1 className="text-5xl font-extrabold text-[#FAFAFA] mb-4 leading-none" style={{ fontFamily: 'var(--font-bricolage)' }}>
          The Investment Thesis.
        </h1>
        <p className="text-[#71717A] max-w-2xl text-sm leading-relaxed" style={{ fontFamily: 'var(--font-manrope)' }}>
          Technology metrics alone are a vanity exercise. For a B2B SaaS infrastructure startup, technical breakthroughs 
          must translate directly into business leverage. Here is how HELIOS's engineering translates into 
          best-in-class startup metrics across Unit Economics, Retention, and Sales Efficiency.
        </p>
      </div>

      {/* Metrics Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-px bg-[#27272A] border border-[#27272A]">
        {METRICS.map((m, i) => (
          <div key={i} className="bg-[#111117] p-8 hover:bg-[#18181F] transition-colors group">
            <div className="flex justify-between items-start mb-6">
              <div 
                className="w-10 h-10 rounded-none border flex items-center justify-center transition-colors"
                style={{ borderColor: m.color, backgroundColor: `${m.color}10`, color: m.color }}
              >
                <m.icon size={20} />
              </div>
              <div className="text-[10px] uppercase tracking-widest text-[#71717A] px-2 py-1 border border-[#27272A]" style={{ fontFamily: 'var(--font-spacemono)' }}>
                {m.tech}
              </div>
            </div>
            
            <h2 className="text-2xl font-extrabold text-[#FAFAFA] mb-2" style={{ fontFamily: 'var(--font-bricolage)' }}>
              {m.title}
            </h2>
            <div className="text-lg font-bold mb-4" style={{ fontFamily: 'var(--font-spacemono)', color: m.color }}>
              Target: {m.metric}
            </div>
            <p className="text-[#FAFAFA]/70 text-sm leading-relaxed" style={{ fontFamily: 'var(--font-manrope)' }}>
              {m.desc}
            </p>
          </div>
        ))}
      </div>

      {/* Rule of 40 Section */}
      <section className="border border-[#27272A] bg-[#09090B] p-8 md:p-12">
        <div className="max-w-3xl">
          <h3 className="text-[10px] uppercase tracking-widest text-[#FF4D00] mb-3" style={{ fontFamily: 'var(--font-spacemono)' }}>
            Series A Trajectory
          </h3>
          <h2 className="text-3xl font-extrabold text-[#FAFAFA] mb-6" style={{ fontFamily: 'var(--font-bricolage)' }}>
            Engineering for the Rule of 40
          </h2>
          <p className="text-[#71717A] text-sm leading-relaxed mb-8" style={{ fontFamily: 'var(--font-manrope)' }}>
            The <strong>Rule of 40</strong> states that a successful SaaS company's growth rate plus profit margin should exceed 40%. 
            Because HELIOS's architecture minimizes cloud spend (high margin) and uses LLM synthesis to reduce integration friction (high growth), 
            the platform is fundamentally engineered to exceed this benchmark from Seed to Series B.
          </p>
          
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-px bg-[#27272A]">
            <div className="bg-[#111117] p-6 text-center">
              <div className="text-3xl font-extrabold text-[#FAFAFA] mb-2" style={{ fontFamily: 'var(--font-spacemono)' }}>3-5x</div>
              <div className="text-[10px] text-[#71717A] uppercase tracking-widest" style={{ fontFamily: 'var(--font-spacemono)' }}>Target YoY Growth</div>
            </div>
            <div className="bg-[#111117] p-6 text-center">
              <div className="text-3xl font-extrabold text-[#FAFAFA] mb-2" style={{ fontFamily: 'var(--font-spacemono)' }}>&gt; 90%</div>
              <div className="text-[10px] text-[#71717A] uppercase tracking-widest" style={{ fontFamily: 'var(--font-spacemono)' }}>Gross Margin Target</div>
            </div>
            <div className="bg-[#111117] p-6 text-center">
              <div className="text-3xl font-extrabold text-[#FAFAFA] mb-2" style={{ fontFamily: 'var(--font-spacemono)' }}>&lt; 1.5</div>
              <div className="text-[10px] text-[#71717A] uppercase tracking-widest" style={{ fontFamily: 'var(--font-spacemono)' }}>Burn Multiple Goal</div>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
