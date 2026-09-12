// Synthetic data generators for HELIOS simulation

export const DAG_STEPS = ['trigger', 'validate', 'persist', 'execute', 'publish'] as const;
export type DagStep = typeof DAG_STEPS[number];
export type CellState = 'idle' | 'pending' | 'executing' | 'done' | 'fault';
export type LogLevel = 'success' | 'info' | 'warn' | 'fault';

export interface LogEntry {
  id: string;
  time: string;
  level: LogLevel;
  event: string;
  detail: string;
}

export interface DagRow {
  threadId: string;
  cells: CellState[];
}

function pad(n: number) {
  return String(n).padStart(2, '0');
}

export function nowMs(): string {
  const d = new Date();
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${String(d.getMilliseconds()).padStart(3, '0')}`;
}

export function generateThroughputSample(base = 500): number {
  // jitter ±6%
  return Math.round(base * (0.94 + Math.random() * 0.12));
}

export function generateLatencySample(faultActive = false): number {
  if (faultActive) return Math.round(280 + Math.random() * 80);
  return Math.round(110 + Math.random() * 50); // 110-160ms normal range
}

export function generateWorkflowId(): string {
  return `wf-${Math.random().toString(36).slice(2, 9)}`;
}

export function makeLogEntry(
  level: LogLevel,
  event: string,
  detail: string,
): LogEntry {
  return {
    id: Math.random().toString(36).slice(2),
    time: nowMs(),
    level,
    event,
    detail,
  };
}

export function initialDagRows(threadCount = 3): DagRow[] {
  return Array.from({ length: threadCount }, (_, i) => ({
    threadId: `pool-${i + 1}`,
    cells: Array(DAG_STEPS.length).fill('idle') as CellState[],
  }));
}
