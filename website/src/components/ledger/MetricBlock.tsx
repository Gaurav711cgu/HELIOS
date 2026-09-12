import { cn } from '@/lib/utils';
import { type LucideIcon } from 'lucide-react';

type MetricStatus = 'ok' | 'warn' | 'breach';
type MetricTrend = 'up' | 'down' | 'neutral';

interface MetricBlockProps {
  label: string;
  value: string;
  current: string;
  trend: MetricTrend;
  status: MetricStatus;
  icon: LucideIcon;
}

const statusTopBorder: Record<MetricStatus, string> = {
  ok:     'border-t-[#22C55E]',
  warn:   'border-t-[#EAB308]',
  breach: 'border-t-[#FF4D00]',
};

const trendColor: Record<MetricTrend, string> = {
  up:      'text-[#22C55E]',
  down:    'text-[#3E6DB4]',
  neutral: 'text-[#71717A]',
};

const trendGlyph: Record<MetricTrend, string> = {
  up:      '↑',
  down:    '↓',
  neutral: '−',
};

export function MetricBlock({ label, value, current, trend, status, icon: Icon }: MetricBlockProps) {
  return (
    <div
      className={cn(
        'border border-[#27272A] border-t-2 bg-[#111117] p-4 flex flex-col justify-between gap-4',
        statusTopBorder[status],
      )}
    >
      <div className="flex items-center justify-between">
        <span
          className="text-[10px] tracking-[0.15em] uppercase text-[#71717A]"
          style={{ fontFamily: 'var(--font-spacemono)' }}
        >
          {label}
        </span>
        <Icon size={14} className="text-[#71717A]" aria-hidden="true" />
      </div>

      <div
        className="text-5xl font-extrabold text-[#FAFAFA] leading-none"
        style={{ fontFamily: 'var(--font-bricolage)' }}
        aria-label={`${label}: ${value}`}
      >
        {value}
      </div>

      <div
        className="flex items-center gap-1.5 text-xs"
        style={{ fontFamily: 'var(--font-spacemono)' }}
      >
        <span className={cn('font-bold', trendColor[trend])}>
          {trendGlyph[trend]}
        </span>
        <span className="text-[#71717A]">{current}</span>
      </div>
    </div>
  );
}
