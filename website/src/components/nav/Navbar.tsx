'use client';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';

const NAV_LINKS = [
  { href: '/',             label: 'Dashboard' },
  { href: '/metrics',      label: 'Metrics' },
  { href: '/simulation',   label: 'Simulation' },
  { href: '/architecture', label: 'Architecture' },
  { href: '/challenges',   label: 'Challenges' },
  { href: '/compare',      label: 'Compare' },
  { href: '/investors',    label: 'Business Case' },
];

export function Navbar() {
  const pathname = usePathname();

  return (
    <nav
      className="border-b border-[#27272A] bg-[#09090B] px-6 flex items-center justify-between h-14 sticky top-0 z-50"
      aria-label="Main navigation"
    >
      {/* Brand */}
      <Link
        href="/"
        className="flex items-center gap-3 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#FF4D00]"
        aria-label="HELIOS home"
      >
        <span
          className="text-xl font-extrabold text-[#FAFAFA] uppercase tracking-tight"
          style={{ fontFamily: 'var(--font-bricolage)' }}
        >
          HELIOS
        </span>
        <span
          className="text-[9px] border border-[#27272A] px-1.5 py-0.5 text-[#71717A] tracking-widest hidden sm:inline"
          style={{ fontFamily: 'var(--font-spacemono)' }}
        >
          v1.0
        </span>
      </Link>

      {/* Links - Scrollable on mobile */}
      <div className="flex-1 overflow-x-auto no-scrollbar mx-4">
        <ol className="flex items-center gap-1 w-max" role="list">
          {NAV_LINKS.map(({ href, label }) => {
            const active = pathname === href;
            return (
              <li key={href}>
                <Link
                  href={href}
                  className={cn(
                    'px-3 py-1.5 text-[11px] tracking-widest uppercase transition-colors duration-100',
                    'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#FF4D00]',
                    active
                      ? 'text-[#FF4D00] border border-[#FF4D00]/40 bg-[#FF4D00]/5'
                      : 'text-[#71717A] hover:text-[#FAFAFA] border border-transparent',
                  )}
                  style={{ fontFamily: 'var(--font-spacemono)' }}
                  aria-current={active ? 'page' : undefined}
                >
                  {label}
                </Link>
              </li>
            );
          })}
        </ol>
      </div>

      {/* Status pill */}
      <div
        className="hidden lg:flex items-center gap-2 text-[10px] text-[#22C55E] shrink-0"
        style={{ fontFamily: 'var(--font-spacemono)' }}
        aria-label="System status: operational"
      >
        <span className="w-2 h-2 bg-[#22C55E] animate-pulse rounded-none" aria-hidden="true" />
        OPERATIONAL
      </div>
    </nav>
  );
}
