'use client';
import { useEngine } from '@/lib/useEngine';
import { PanelShell } from '@/components/ui/PanelShell';
import { DAG_STEPS, type CellState } from '@/lib/simulationData';
import { cn } from '@/lib/utils';

const cellConfig: Record<CellState, { bg: string; text: string; glyph: string; ariaLabel: string }> = {
  idle:      { bg: 'bg-[#1C1C22]', text: 'text-[#27272A]', glyph: '○', ariaLabel: 'idle' },
  pending:   { bg: 'bg-[#1C1C22]', text: 'text-[#3E6DB4]/60', glyph: '○', ariaLabel: 'pending' },
  executing: { bg: 'bg-[#3E6DB4]/15', text: 'text-[#3E6DB4]', glyph: '◉', ariaLabel: 'executing' },
  done:      { bg: 'bg-[#22C55E]/10', text: 'text-[#22C55E]', glyph: '●', ariaLabel: 'complete' },
  fault:     { bg: 'bg-[#EAB308]/10', text: 'text-[#EAB308]', glyph: '✕', ariaLabel: 'fault' },
};

export function DagMatrix() {
  const dagRows = useEngine(s => s.dagRows);
  const isRunning = useEngine(s => s.isRunning);
  const faultActive = useEngine(s => s.faultActive);

  return (
    <PanelShell
      title="DAG Execution Matrix — Thread × Step"
      status={faultActive ? 'fault' : isRunning ? 'live' : 'idle'}
      aria-label="DAG execution thread-step matrix"
      className="h-full"
    >
      <div className="p-4 overflow-x-auto">
        {/* Header row */}
        <div className="grid gap-px mb-2" style={{ gridTemplateColumns: `100px repeat(${DAG_STEPS.length}, 1fr)` }}>
          <div
            className="text-[10px] text-[#71717A] uppercase tracking-widest py-2"
            style={{ fontFamily: 'var(--font-spacemono)' }}
          >
            THREAD
          </div>
          {DAG_STEPS.map(step => (
            <div
              key={step}
              className="text-[10px] text-[#71717A] uppercase tracking-widest text-center py-2"
              style={{ fontFamily: 'var(--font-spacemono)' }}
            >
              {step}
            </div>
          ))}
        </div>

        {/* Data rows */}
        <div className="flex flex-col gap-2">
          {dagRows.map((row) => (
            <div
              key={row.threadId}
              className="grid gap-px"
              style={{ gridTemplateColumns: `100px repeat(${DAG_STEPS.length}, 1fr)` }}
            >
              {/* Thread label */}
              <div
                className="flex items-center text-[10px] text-[#71717A] pr-2 py-3"
                style={{ fontFamily: 'var(--font-spacemono)' }}
              >
                {row.threadId}
              </div>

              {/* Step cells */}
              {row.cells.map((cell, i) => {
                const cfg = cellConfig[cell];
                return (
                  <div
                    key={i}
                    className={cn(
                      'flex items-center justify-center py-3 text-lg transition-all duration-300 border border-[#27272A]',
                      cfg.bg,
                      cfg.text,
                      cell === 'executing' && 'animate-pulse',
                    )}
                    aria-label={`${DAG_STEPS[i]} ${cfg.ariaLabel} on ${row.threadId}`}
                    role="cell"
                  >
                    {cfg.glyph}
                  </div>
                );
              })}
            </div>
          ))}
        </div>

        {/* Legend */}
        <div
          className="flex gap-4 mt-4 pt-3 border-t border-[#27272A] text-[10px] text-[#71717A]"
          style={{ fontFamily: 'var(--font-spacemono)' }}
        >
          <span><span className="text-[#27272A]">○</span> IDLE</span>
          <span><span className="text-[#3E6DB4]">◉</span> EXECUTING</span>
          <span><span className="text-[#22C55E]">●</span> DONE</span>
          <span><span className="text-[#EAB308]">✕</span> FAULT</span>
        </div>
      </div>
    </PanelShell>
  );
}
