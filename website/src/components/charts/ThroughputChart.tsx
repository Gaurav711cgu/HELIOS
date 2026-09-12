'use client';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, ReferenceLine,
} from 'recharts';
import { useEngine } from '@/lib/useEngine';
import { PanelShell } from '@/components/ui/PanelShell';

const CustomTooltip = ({ active, payload, label }: any) => {
  if (!active || !payload?.length) return null;
  return (
    <div
      className="border border-[#27272A] bg-[#09090B] px-3 py-2 text-xs"
      style={{ fontFamily: 'var(--font-spacemono)' }}
    >
      <p className="text-[#71717A] mb-1">{label}</p>
      <p className="text-[#FF4D00]">{payload[0].value} wf/sec</p>
    </div>
  );
};

export function ThroughputChart() {
  const history = useEngine(s => s.throughputHistory);
  const isRunning = useEngine(s => s.isRunning);
  const benchmarkActive = useEngine(s => s.benchmarkActive);

  return (
    <PanelShell
      title="Throughput Engine — Workflows/sec"
      status={isRunning ? 'live' : 'idle'}
      aria-label="Throughput time-series chart"
      className="h-full"
    >
      <div className="p-4 h-64">
        {history.length === 0 ? (
          <div className="h-full flex items-center justify-center">
            <span
              className="text-[#71717A] text-xs tracking-widest"
              style={{ fontFamily: 'var(--font-spacemono)' }}
            >
              AWAITING SIMULATION START
            </span>
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={history} margin={{ top: 8, right: 0, left: -20, bottom: 0 }}>
              <defs>
                <linearGradient id="throughputFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#FF4D00" stopOpacity={0.25} />
                  <stop offset="95%" stopColor="#FF4D00" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke="#27272A" strokeDasharray="4 4" vertical={false} />
              <XAxis
                dataKey="time"
                stroke="#27272A"
                tick={{ fill: '#71717A', fontSize: 10, fontFamily: 'var(--font-spacemono)' }}
                tickLine={false}
                interval="preserveStartEnd"
              />
              <YAxis
                domain={[0, 650]}
                stroke="#27272A"
                tick={{ fill: '#71717A', fontSize: 10, fontFamily: 'var(--font-spacemono)' }}
                tickLine={false}
                axisLine={false}
              />
              <Tooltip content={<CustomTooltip />} />
              <ReferenceLine
                y={benchmarkActive ? 580 : 500}
                stroke="#71717A"
                strokeDasharray="6 3"
                label={{
                  value: benchmarkActive ? 'BENCHMARK 580' : 'TARGET 500',
                  position: 'right',
                  fill: '#71717A',
                  fontSize: 9,
                  fontFamily: 'var(--font-spacemono)',
                }}
              />
              <Area
                type="stepAfter"
                dataKey="val"
                stroke="#FF4D00"
                strokeWidth={2}
                fill="url(#throughputFill)"
                dot={false}
                animationDuration={300}
              />
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>
    </PanelShell>
  );
}
