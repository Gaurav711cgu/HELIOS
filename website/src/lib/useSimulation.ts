'use client';
import { create } from 'zustand';
import {
  generateThroughputSample,
  generateLatencySample,
  generateWorkflowId,
  makeLogEntry,
  initialDagRows,
  type LogEntry,
  type DagRow,
  type CellState,
  DAG_STEPS,
} from './simulationData';

const MAX_HISTORY = 60;
const MAX_LOGS = 50;

interface HistoryPoint { time: string; val: number; }
interface LatencyPoint { time: string; p99: number; }

interface SimulationState {
  isRunning: boolean;
  faultActive: boolean;
  benchmarkActive: boolean;
  totalWorkflows: number;
  throughputHistory: HistoryPoint[];
  latencyHistory: LatencyPoint[];
  dagRows: DagRow[];
  logs: LogEntry[];
  // Actions
  startSimulation: () => void;
  stopSimulation: () => void;
  injectFault: () => void;
  runBenchmark: () => void;
}

let throughputInterval: ReturnType<typeof setInterval> | null = null;
let dagInterval: ReturnType<typeof setInterval> | null = null;
let faultTimeout: ReturnType<typeof setTimeout> | null = null;
let benchmarkTimeout: ReturnType<typeof setTimeout> | null = null;

function timeLabel(): string {
  const d = new Date();
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`;
}

function pushLog(state: SimulationState, entry: LogEntry): LogEntry[] {
  return [entry, ...state.logs].slice(0, MAX_LOGS);
}

export const useSimulation = create<SimulationState>((set, get) => ({
  isRunning: false,
  faultActive: false,
  benchmarkActive: false,
  totalWorkflows: 0,
  throughputHistory: [],
  latencyHistory: [],
  dagRows: initialDagRows(3),
  logs: [
    makeLogEntry('info', 'SYSTEM', 'Helios orchestrator v1.0 ready'),
  ],

  startSimulation: () => {
    if (get().isRunning) return;
    set({ isRunning: true });

    // Add start log
    set(s => ({
      logs: pushLog(s, makeLogEntry('success', 'STARTED', 'Simulation engine initialised')),
    }));

    // Throughput + latency tick every second
    throughputInterval = setInterval(() => {
      const { faultActive, benchmarkActive } = get();
      const base = benchmarkActive ? 580 : 500;
      const tVal = generateThroughputSample(base);
      const lVal = generateLatencySample(faultActive);
      const label = timeLabel();
      set(s => ({
        totalWorkflows: s.totalWorkflows + tVal,
        throughputHistory: [
          ...s.throughputHistory.slice(-(MAX_HISTORY - 1)),
          { time: label, val: tVal },
        ],
        latencyHistory: [
          ...s.latencyHistory.slice(-(MAX_HISTORY - 1)),
          { time: label, p99: lVal },
        ],
      }));
    }, 1000);

    // DAG step animation every 600ms
    dagInterval = setInterval(() => {
      const { dagRows, faultActive } = get();
      const wfId = generateWorkflowId();
      const newRows = dagRows.map((row) => {
        const cells = [...row.cells] as CellState[];
        // Find first non-done cell
        const idx = cells.findIndex(c => c !== 'done');
        if (idx === -1) {
          // Reset row for next workflow
          const newId = generateWorkflowId();
          set(s => ({
            logs: pushLog(s, makeLogEntry('success', 'PUBLISHED', `${newId} completed all steps`)),
          }));
          return { ...row, cells: Array(DAG_STEPS.length).fill('idle') as CellState[] };
        }
        if (cells[idx] === 'idle' || cells[idx] === 'fault') {
          cells[idx] = 'executing';
          if (idx === 0) {
            set(s => ({ logs: pushLog(s, makeLogEntry('info', 'TRIGGERED', `${wfId} dispatched to ${row.threadId}`)) }));
          }
        } else if (cells[idx] === 'executing') {
          // Random fault injection on persist step if fault is active
          if (faultActive && idx === 2 && Math.random() < 0.4) {
            cells[idx] = 'fault';
            set(s => ({ logs: pushLog(s, makeLogEntry('fault', 'FAULT', `${row.threadId} partition failure at ${DAG_STEPS[idx]}`)) }));
          } else {
            cells[idx] = 'done';
            set(s => ({ logs: pushLog(s, makeLogEntry('success', DAG_STEPS[idx].toUpperCase(), `${row.threadId} step complete`)) }));
          }
        }
        return { ...row, cells };
      });
      set({ dagRows: newRows });
    }, 600);
  },

  stopSimulation: () => {
    if (throughputInterval) clearInterval(throughputInterval);
    if (dagInterval) clearInterval(dagInterval);
    set({ isRunning: false });
  },

  injectFault: () => {
    if (get().faultActive) return;
    set(s => ({
      faultActive: true,
      logs: pushLog(s, makeLogEntry('fault', 'CHAOS', 'Network partition injected — exactly-once guard active')),
    }));
    if (faultTimeout) clearTimeout(faultTimeout);
    faultTimeout = setTimeout(() => {
      set(s => ({
        faultActive: false,
        logs: pushLog(s, makeLogEntry('success', 'RECOVERED', 'Kafka TX committed — 0 duplicate executions')),
      }));
    }, 4000);
  },

  runBenchmark: () => {
    if (get().benchmarkActive) return;
    set(s => ({
      benchmarkActive: true,
      logs: pushLog(s, makeLogEntry('info', 'BENCHMARK', 'Throughput spike test started (580/sec target)')),
    }));
    if (benchmarkTimeout) clearTimeout(benchmarkTimeout);
    benchmarkTimeout = setTimeout(() => {
      set(s => ({
        benchmarkActive: false,
        logs: pushLog(s, makeLogEntry('success', 'BENCHMARK', 'Spike test complete — SLA maintained')),
      }));
    }, 10000);
  },
}));
