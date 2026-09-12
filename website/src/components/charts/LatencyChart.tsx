'use client';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, ReferenceLine,
} from 'recharts';
import { useEngine } from '@/lib/useEngine';
import { PanelShell } from '@/components/ui/PanelShell';

const CustomTooltip = ({ active, payload, label }: any) => {
  if (!active || !payload?.length) return null;
  const val = payload[0].value as number;
  const breach = val > 150;
  return (
    <div
      className="border border-[#27272A] bg-[#09090B] px-3 py-2 text-xs"
      style={{ fontFamily: 'var(--font-spacemono)' }}
    >
      <p className="text-[#71717A] mb-1">{label}</p>
      <p className={breach ? 'text-[#EAB308]' : 'text-[#3E6DB4]'}>{val} ms P99</p>
    </div>
  );
};

function BreachDot(props: any) {
  const { cx, cy, payload } = props;
  if (!payload || payload.p99 <= 150) return null;
  return <circle cx={cx} cy={cy} r={4} fill="#EAB308" stroke="#09090B" strokeWidth={2} />;
}

export function LatencyChart() {
  const history = useEngine(s => s.latencyHistory);
  const isRunning = useEngine(s => s.isRunning);
  const faultActive = useEngine(s => s.faultActive);

  return (
    <PanelShell
      title="P99 Latency vs SLA — ms"
      status={faultActive ? 'fault' : isRunning ? 'live' : 'idle'}
      aria-label="P99 latency chart with 150ms SLA reference line"
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
            <LineChart data={history} margin={{ top: 8, right: 0, left: -20, bottom: 0 }}>
              <CartesianGrid stroke="#27272A" strokeDasharray="4 4" vertical={false} />
              <XAxis
                dataKey="time"
                stroke="#27272A"
                tick={{ fill: '#71717A', fontSize: 10, fontFamily: 'var(--font-spacemono)' }}
                tickLine={false}
                interval="preserveStartEnd"
              />
              <YAxis
                domain={[0, 300]}
                stroke="#27272A"
                tick={{ fill: '#71717A', fontSize: 10, fontFamily: 'var(--font-spacemono)' }}
                tickLine={false}
                axisLine={false}
              />
              <Tooltip content={<CustomTooltip />} />
              <ReferenceLine
                y={150}
                stroke="#FF4D00"
                strokeDasharray="5 3"
                label={{
                  value: 'SLA LIMIT 150ms',
                  position: 'right',
                  fill: '#FF4D00',
                  fontSize: 9,
                  fontFamily: 'var(--font-spacemono)',
                }}
              />
              <Line
                type="stepAfter"
                dataKey="p99"
                stroke="#3E6DB4"
                strokeWidth={2}
                dot={<BreachDot />}
                activeDot={{ r: 4, fill: '#3E6DB4' }}
                animationDuration={300}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>
    </PanelShell>
  );
}
