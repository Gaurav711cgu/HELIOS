'use client';
import { useEffect, useRef } from 'react';
import { useEngine } from '@/lib/useEngine';

export function HudHeader() {
  const totalWorkflows = useEngine(s => s.totalWorkflows);
  const isRunning = useEngine(s => s.isRunning);
  const isBackendConnected = useEngine(s => s.isBackendConnected);
  const counterRef = useRef<HTMLSpanElement>(null);

  // Smoothly format number with commas
  const formatted = totalWorkflows.toLocaleString('en-US');

  return (
    <header
      className="border-b border-[#27272A] bg-[#111117] px-6 py-4 flex items-center justify-between"
      role="banner"
    >
      {/* Brand */}
      <div className="flex items-center gap-4">
        <h1
          className="text-2xl font-extrabold tracking-tight text-[#FAFAFA] uppercase"
          style={{ fontFamily: 'var(--font-bricolage)', letterSpacing: '-0.01em' }}
        >
          Helios
        </h1>
        <span
          className="text-[10px] border border-[#27272A] px-2 py-1 text-[#71717A] tracking-widest"
          style={{ fontFamily: 'var(--font-spacemono)', borderRadius: 0 }}
        >
          {isBackendConnected ? 'v1.0 / LIVE (SPRING BOOT)' : 'v1.0 / ORCHESTRATOR'}
        </span>
      </div>

      {/* Right ticker */}
      <div
        className="flex items-center gap-6 text-[11px] text-[#71717A]"
        style={{ fontFamily: 'var(--font-spacemono)' }}
      >
        <div aria-live="polite" aria-label={`Total workflows processed: ${formatted}`}>
          <span className="text-[#FAFAFA] text-sm font-bold" ref={counterRef}>
            {formatted}
          </span>
          <span className="ml-2 tracking-widest">WORKFLOWS PROCESSED</span>
        </div>

        <div className="flex items-center gap-2">
          <span
            className={isRunning 
              ? (isBackendConnected ? 'w-2 h-2 rounded-none bg-[#22C55E] animate-pulse' : 'w-2 h-2 rounded-none bg-[#EAB308] animate-pulse') 
              : 'w-2 h-2 rounded-none bg-[#27272A]'}
            aria-hidden="true"
          />
          <span className="tracking-widest text-[#71717A]">
            {isRunning ? (isBackendConnected ? 'OPERATIONAL' : 'POLLING...') : 'STANDBY'}
          </span>
        </div>
      </div>
    </header>
  );
}
