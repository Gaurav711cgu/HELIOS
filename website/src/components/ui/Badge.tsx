import { cn } from '@/lib/utils';

type BadgeVariant = 'running' | 'pending' | 'fault' | 'recovered' | 'idle';

const variantStyles: Record<BadgeVariant, string> = {
  running:   'bg-[#22C55E]/15 text-[#22C55E] border-[#22C55E]/30',
  pending:   'bg-[#3E6DB4]/15 text-[#3E6DB4] border-[#3E6DB4]/30',
  fault:     'bg-[#EAB308]/15 text-[#EAB308] border-[#EAB308]/30',
  recovered: 'bg-[#22C55E]/10 text-[#22C55E]/70 border-[#22C55E]/20',
  idle:      'bg-[#27272A]/50 text-[#71717A] border-[#27272A]',
};

const labels: Record<BadgeVariant, string> = {
  running:   'RUNNING',
  pending:   'PENDING',
  fault:     'FAULT',
  recovered: 'RECOVERED',
  idle:      'IDLE',
};

interface BadgeProps {
  variant: BadgeVariant;
  className?: string;
}

export function Badge({ variant, className }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center border px-2 py-0.5 text-[10px] tracking-widest',
        variantStyles[variant],
        className,
      )}
      style={{ fontFamily: 'var(--font-spacemono)', borderRadius: 0 }}
    >
      {labels[variant]}
    </span>
  );
}
