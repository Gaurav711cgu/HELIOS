import { cn } from '@/lib/utils';

interface PanelShellProps {
  title?: string;
  status?: 'live' | 'idle' | 'fault';
  className?: string;
  children: React.ReactNode;
  'aria-label'?: string;
}

const statusBorder: Record<string, string> = {
  live:  'border-t-[#22C55E]',
  fault: 'border-t-[#EAB308]',
  idle:  'border-t-[#27272A]',
};

export function PanelShell({
  title,
  status = 'idle',
  className,
  children,
  'aria-label': ariaLabel,
}: PanelShellProps) {
  return (
    <section
      aria-label={ariaLabel ?? title}
      className={cn(
        'border border-[#27272A] border-t-2 bg-[#111117] flex flex-col',
        statusBorder[status],
        className,
      )}
    >
      {title && (
        <header className="px-4 py-3 border-b border-[#27272A] flex items-center justify-between">
          <span
            className="text-[11px] tracking-[0.15em] uppercase text-[#71717A]"
            style={{ fontFamily: 'var(--font-spacemono)' }}
          >
            {title}
          </span>
          {status !== 'idle' && (
            <span
              className={cn(
                'w-1.5 h-1.5 rounded-full',
                status === 'live'  && 'bg-[#22C55E] animate-pulse',
                status === 'fault' && 'bg-[#EAB308] animate-pulse',
              )}
              aria-hidden="true"
            />
          )}
        </header>
      )}
      <div className="flex-1 min-h-0">{children}</div>
    </section>
  );
}
