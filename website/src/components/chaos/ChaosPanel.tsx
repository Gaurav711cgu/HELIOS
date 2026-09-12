'use client';
import { Zap, FlaskConical } from 'lucide-react';
import { useEngine } from '@/lib/useEngine';
import { PanelShell } from '@/components/ui/PanelShell';
import { TerminalButton } from '@/components/ui/TerminalButton';

export function ChaosPanel() {
  const isRunning = useEngine(s => s.isRunning);
  const faultActive = useEngine(s => s.faultActive);
  const benchmarkActive = useEngine(s => s.benchmarkActive);
  const startSimulation = useEngine(s => s.startSimulation);
  const injectFault = useEngine(s => s.injectFault);
  const runBenchmark = useEngine(s => s.runBenchmark);

  return (
    <PanelShell
      title="Chaos & Benchmark Controls"
      status={faultActive ? 'fault' : 'idle'}
      aria-label="Chaos testing and benchmark controls"
      className="h-full"
    >
      <div className="p-4 flex flex-col gap-4">
        {/* Start simulation */}
        {!isRunning && (
          <div>
            <TerminalButton
              variant="primary"
              className="w-full justify-center"
              onClick={startSimulation}
              icon={<Zap size={12} />}
            >
              Start Simulation
            </TerminalButton>
            <p
              className="text-[10px] text-[#71717A] mt-2"
              style={{ fontFamily: 'var(--font-spacemono)' }}
            >
              Initialises simulation engine and begins emitting metrics.
            </p>
          </div>
        )}

        {/* Fault injection */}
        <div>
          <TerminalButton
            variant="destructive"
            className="w-full justify-center"
            onClick={injectFault}
            isLoading={faultActive}
            disabled={!isRunning || faultActive}
            icon={<Zap size={12} />}
            aria-label={faultActive ? 'Fault injection in progress' : 'Inject network partition fault'}
          >
            {faultActive ? 'Fault Active…' : 'Inject Fault'}
          </TerminalButton>
          <p
            className="text-[10px] text-[#71717A] mt-2"
            style={{ fontFamily: 'var(--font-spacemono)' }}
          >
            Simulates network partition. Exactly-once guard triggers — watch latency spike and recovery.
          </p>
        </div>

        {/* Benchmark */}
        <div>
          <TerminalButton
            variant="primary"
            className="w-full justify-center"
            onClick={runBenchmark}
            isLoading={benchmarkActive}
            disabled={!isRunning || benchmarkActive}
            icon={<FlaskConical size={12} />}
            aria-label={benchmarkActive ? 'Benchmark running' : 'Run throughput benchmark'}
          >
            {benchmarkActive ? 'Benchmark Running…' : 'Run Benchmark'}
          </TerminalButton>
          <p
            className="text-[10px] text-[#71717A] mt-2"
            style={{ fontFamily: 'var(--font-spacemono)' }}
          >
            10-second spike to 580 wf/sec. Validates burst capacity above 500/sec target.
          </p>
        </div>

        {/* Benchmark ledger */}
        <div
          className="mt-2 pt-4 border-t border-[#27272A] space-y-2 text-[10px]"
          style={{ fontFamily: 'var(--font-spacemono)' }}
        >
          <div className="text-[#71717A] tracking-widest uppercase mb-3">Benchmark Ledger</div>
          {[
            ['Exactly-once chaos', '1,000 faults → 0 dupes'],
            ['Throughput target',  '500 wf/sec for 5 min'],
            ['Latency SLA',        'P99 &lt; 150 ms'],
            ['Crash recovery',     'P99 &lt; 5 sec'],
            ['AI DAG synthesis',   '40/50 valid first pass'],
          ].map(([label, val]) => (
            <div key={label} className="flex justify-between gap-2">
              <span className="text-[#71717A]">{label}</span>
              <span
                className="text-[#FAFAFA] text-right"
                dangerouslySetInnerHTML={{ __html: val }}
              />
            </div>
          ))}
        </div>
      </div>
    </PanelShell>
  );
}
