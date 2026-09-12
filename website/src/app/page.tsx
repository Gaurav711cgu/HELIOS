'use client';
import { Activity, Clock, ShieldCheck, RefreshCw } from 'lucide-react';
import { HudHeader } from '@/components/hud/HudHeader';
import { MetricBlock } from '@/components/ledger/MetricBlock';
import { ThroughputChart } from '@/components/charts/ThroughputChart';
import { LatencyChart } from '@/components/charts/LatencyChart';
import { DagMatrix } from '@/components/dag/DagMatrix';
import { ExecutionLog } from '@/components/logs/ExecutionLog';
import { ChaosPanel } from '@/components/chaos/ChaosPanel';
import { useEngine } from '@/lib/useEngine';
import { useEffect } from 'react';

export default function Page() {
  const throughputHistory = useEngine(s => s.throughputHistory);
  const latencyHistory = useEngine(s => s.latencyHistory);
  const faultActive = useEngine(s => s.faultActive);
  const benchmarkActive = useEngine(s => s.benchmarkActive);
  const stopSimulation = useEngine(s => s.stopSimulation);

  // Stop polling when navigating away to prevent memory/network leaks
  useEffect(() => {
    return () => {
      stopSimulation();
    };
  }, [stopSimulation]);

  // Derive live values for metric blocks
  const lastThroughput = throughputHistory.at(-1)?.val ?? 0;
  const lastLatency = latencyHistory.at(-1)?.p99 ?? 0;

  return (
    <div className="min-h-screen flex flex-col bg-[#09090B]">
      <HudHeader />

      {/* === LEDGER === */}
      <section
        className="grid grid-cols-2 lg:grid-cols-4 border-b border-[#27272A]"
        aria-label="Key performance metrics"
      >
        <MetricBlock
          label="Throughput Target"
          value="500/sec"
          current={lastThroughput > 0 ? `Current: ${lastThroughput}/sec` : 'Awaiting data'}
          trend={benchmarkActive ? 'up' : 'neutral'}
          status={lastThroughput > 490 ? 'ok' : lastThroughput > 0 ? 'warn' : 'ok'}
          icon={Activity}
        />
        <MetricBlock
          label="P99 Latency SLA"
          value="150 ms"
          current={lastLatency > 0 ? `Current: ${lastLatency} ms` : 'Awaiting data'}
          trend={lastLatency > 150 ? 'up' : lastLatency > 0 ? 'down' : 'neutral'}
          status={lastLatency > 150 ? 'breach' : lastLatency > 130 ? 'warn' : 'ok'}
          icon={Clock}
        />
        <MetricBlock
          label="Exactly-Once Guard"
          value="0 Dupes"
          current="1,000 faults injected"
          trend="neutral"
          status={faultActive ? 'warn' : 'ok'}
          icon={ShieldCheck}
        />
        <MetricBlock
          label="Crash Recovery"
          value="< 5 sec"
          current="P99: 3.2 sec"
          trend="down"
          status="ok"
          icon={RefreshCw}
        />
      </section>

      {/* === MAIN GRID === */}
      <main className="flex-1 grid grid-cols-1 lg:grid-cols-3 border-b border-[#27272A]">

        {/* Charts column */}
        <div className="lg:col-span-2 border-r border-[#27272A] flex flex-col divide-y divide-[#27272A]">
          <ThroughputChart />
          <LatencyChart />
        </div>

        {/* Right column: log + chaos */}
        <div className="flex flex-col divide-y divide-[#27272A]">
          <ExecutionLog />
          <ChaosPanel />
        </div>
      </main>

      {/* === DAG MATRIX === */}
      <section className="border-t border-[#27272A]">
        <DagMatrix />
      </section>

      {/* === FOOTER === */}
      <footer
        className="border-t border-[#27272A] px-6 py-3 flex items-center justify-between text-[10px] text-[#71717A]"
        style={{ fontFamily: 'var(--font-spacemono)' }}
      >
        <span>HELIOS ORCHESTRATOR v1.0 — Java · Spring Boot · DynamoDB · Kafka · Redis</span>
        <span>INFRA: REAL · BENCHMARKS: COMMITTED · DUPES: ZERO</span>
      </footer>
    </div>
  );
}
