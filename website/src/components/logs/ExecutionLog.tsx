'use client';
import { motion, AnimatePresence } from 'framer-motion';
import { useEngine } from '@/lib/useEngine';
import { PanelShell } from '@/components/ui/PanelShell';
import { type LogLevel } from '@/lib/simulationData';
import { cn } from '@/lib/utils';

const levelStyles: Record<LogLevel, { border: string; event: string; detail: string }> = {
  success: { border: 'border-l-[#22C55E]', event: 'text-[#22C55E]', detail: 'text-[#FAFAFA]/80' },
  info:    { border: 'border-l-[#3E6DB4]',  event: 'text-[#3E6DB4]',  detail: 'text-[#FAFAFA]/80' },
  warn:    { border: 'border-l-[#EAB308]',  event: 'text-[#EAB308]',  detail: 'text-[#EAB308]/80' },
  fault:   { border: 'border-l-[#FF4D00]',  event: 'text-[#FF4D00]',  detail: 'text-[#FF4D00]/80' },
};

export function ExecutionLog() {
  const logs = useEngine(s => s.logs);
  const isRunning = useEngine(s => s.isRunning);

  return (
    <PanelShell
      title="Execution Log"
      status={isRunning ? 'live' : 'idle'}
      aria-label="Live workflow execution log"
      className="h-full"
    >
      <div
        className="h-80 overflow-y-auto px-4 py-3 flex flex-col gap-2"
        aria-live="polite"
        aria-label="Log entries"
      >
        <AnimatePresence initial={false}>
          {logs.map((entry) => {
            const styles = levelStyles[entry.level];
            return (
              <motion.div
                key={entry.id}
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.15, ease: 'easeOut' }}
                className={cn('border-l-2 pl-3 py-1.5 min-w-0', styles.border)}
              >
                <div
                  className="flex items-baseline gap-3"
                  style={{ fontFamily: 'var(--font-spacemono)' }}
                >
                  <span className="text-[#71717A] text-[10px] shrink-0">{entry.time}</span>
                  <span className={cn('text-[10px] font-bold tracking-widest shrink-0', styles.event)}>
                    {entry.event}
                  </span>
                  <span className={cn('text-[11px] truncate', styles.detail)}>
                    {entry.detail}
                  </span>
                </div>
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    </PanelShell>
  );
}
