import { cn } from '@/lib/utils';
import { Loader2 } from 'lucide-react';

interface TerminalButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'destructive' | 'ghost';
  isLoading?: boolean;
  icon?: React.ReactNode;
}

const variantStyles: Record<string, string> = {
  primary:     'border-[#FF4D00] text-[#FF4D00] hover:bg-[#FF4D00]/10 disabled:border-[#27272A] disabled:text-[#71717A]',
  destructive: 'border-[#EAB308] text-[#EAB308] hover:bg-[#EAB308]/10 disabled:border-[#27272A] disabled:text-[#71717A]',
  ghost:       'border-[#27272A] text-[#FAFAFA] hover:border-[#71717A] disabled:opacity-50',
};

export function TerminalButton({
  variant = 'ghost',
  isLoading = false,
  icon,
  children,
  disabled,
  className,
  ...props
}: TerminalButtonProps) {
  return (
    <button
      disabled={disabled || isLoading}
      aria-busy={isLoading}
      className={cn(
        'flex items-center gap-2 border px-4 py-2.5 text-xs tracking-widest uppercase transition-colors duration-150 cursor-pointer disabled:cursor-not-allowed',
        variantStyles[variant],
        className,
      )}
      style={{ fontFamily: 'var(--font-spacemono)', borderRadius: 0 }}
      {...props}
    >
      {isLoading ? (
        <Loader2 size={12} className="animate-spin" aria-hidden="true" />
      ) : icon ? (
        <span aria-hidden="true">{icon}</span>
      ) : null}
      {children}
    </button>
  );
}
