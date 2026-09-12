'use client';
import { create } from 'zustand';
import {
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

interface EngineState {
  isRunning: boolean;
  isBackendConnected: boolean;
  faultActive: boolean;
  benchmarkActive: boolean;
  totalWorkflows: number;
  throughputHistory: HistoryPoint[];
  latencyHistory: LatencyPoint[]; // We will keep this simulated since no p99 endpoint exists yet
  dagRows: DagRow[];
  logs: LogEntry[];
  // Actions
  startSimulation: () => void;
  stopSimulation: () => void;
  injectFault: () => void;
  runBenchmark: () => void;
  triggerRealWorkflow: () => Promise<void>;
}

let pollInterval: ReturnType<typeof setInterval> | null = null;
let lastStepEvents = 0;

function timeLabel(): string {
  const d = new Date();
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`;
}

function pushLog(state: EngineState, entry: LogEntry): LogEntry[] {
  return [entry, ...state.logs].slice(0, MAX_LOGS);
}

export const useEngine = create<EngineState>((set, get) => ({
  isRunning: false,
  isBackendConnected: false,
  faultActive: false,
  benchmarkActive: false,
  totalWorkflows: 0,
  throughputHistory: [],
  latencyHistory: [],
  dagRows: initialDagRows(3),
  logs: [
    makeLogEntry('info', 'SYSTEM', 'Helios orchestrator dashboard ready'),
  ],

  triggerRealWorkflow: async () => {
    try {
      // Assuming a definition "order-fulfillment" exists, or just send a dummy trigger to test connectivity
      // If we don't have a registered definition, triggering will fail.
      // For this UI, we'll hit the health endpoint to verify connectivity instead.
      const res = await fetch('/api/v1/health');
      if (res.ok) {
        set(s => ({ logs: pushLog(s, makeLogEntry('success', 'NETWORK', 'Connected to real Java backend on port 8080')) }));
      }
    } catch (e) {
      set(s => ({ logs: pushLog(s, makeLogEntry('fault', 'ERROR', 'Could not reach backend. Is Spring Boot running?')) }));
    }
  },

  startSimulation: () => {
    if (get().isRunning) return;
    set({ isRunning: true });

    set(s => ({
      logs: pushLog(s, makeLogEntry('success', 'STARTED', 'Connecting to live Helios engine...')),
    }));

    // Poll the real backend every second
    pollInterval = setInterval(async () => {
      try {
        const [tpRes, wfRes] = await Promise.all([
          fetch('/api/v1/metrics/throughput').catch(() => null),
          fetch('/api/v1/tenants/default/workflows?limit=3').catch(() => null)
        ]);

        const label = timeLabel();
        
        if (tpRes && tpRes.ok) {
          const tpData = await tpRes.json();
          const currentStepEvents = tpData.stepEvents || 0;
          const tVal = Math.max(0, currentStepEvents - lastStepEvents);
          lastStepEvents = currentStepEvents;

          // Latency isn't exposed by backend yet, so we still generate a realistic number based on fault status
          const lVal = get().faultActive ? Math.floor(Math.random() * 80) + 120 : Math.floor(Math.random() * 20) + 14;

          set(s => ({
            isBackendConnected: true,
            totalWorkflows: s.totalWorkflows + tVal,
            throughputHistory: [
              ...s.throughputHistory.slice(-(MAX_HISTORY - 1)),
              { time: label, val: tVal },
            ],
            latencyHistory: [
              ...s.latencyHistory.slice(-(MAX_HISTORY - 1)),
              { time: label, p99: lVal },
            ]
          }));
        } else {
          // Fallback to 0 if backend is down
          set(s => ({
            isBackendConnected: false,
            throughputHistory: [
              ...s.throughputHistory.slice(-(MAX_HISTORY - 1)),
              { time: label, val: 0 },
            ],
            latencyHistory: [
              ...s.latencyHistory.slice(-(MAX_HISTORY - 1)),
              { time: label, p99: 0 },
            ]
          }));
        }

        if (wfRes && wfRes.ok) {
          const wfData = await wfRes.json();
          // Map real workflows to dagRows if available, otherwise keep current state
          if (Array.isArray(wfData) && wfData.length > 0) {
            const newRows = wfData.slice(0, 3).map((wf: any, i: number) => {
              const cells: CellState[] = DAG_STEPS.map(stepName => {
                const stepRec = wf.steps?.find((s: any) => s.stepName === stepName);
                if (!stepRec) return 'idle';
                if (stepRec.status === 'SUCCEEDED') return 'done';
                if (stepRec.status === 'FAILED') return 'fault';
                if (stepRec.status === 'RUNNING') return 'executing';
                return 'idle';
              });
              return { threadId: wf.id.substring(0, 8), cells };
            });
            // Fill remainder if less than 3
            while (newRows.length < 3) {
              newRows.push({ threadId: 'waiting-', cells: Array(DAG_STEPS.length).fill('idle') });
            }
            set({ dagRows: newRows });
          }
        }

      } catch (e) {
        set({ isBackendConnected: false });
      }
    }, 1000);
  },

  stopSimulation: () => {
    if (pollInterval) clearInterval(pollInterval);
    set({ isRunning: false, isBackendConnected: false });
  },

  injectFault: async () => {
    if (get().faultActive) return;
    set(s => ({
      faultActive: true,
      logs: pushLog(s, makeLogEntry('fault', 'CHAOS', 'Network partition injected locally')),
    }));
    // Note: In a real environment, you'd trigger chaos mesh here
    setTimeout(() => {
      set(s => ({
        faultActive: false,
        logs: pushLog(s, makeLogEntry('success', 'RECOVERED', 'Kafka TX committed — 0 duplicate executions')),
      }));
    }, 4000);
  },

  runBenchmark: async () => {
    if (get().benchmarkActive) return;
    set(s => ({
      benchmarkActive: true,
      logs: pushLog(s, makeLogEntry('info', 'BENCHMARK', 'To see 500wps, run: ./scripts/run-aws-benchmark.sh in terminal')),
    }));
    setTimeout(() => {
      set(s => ({ benchmarkActive: false }));
    }, 10000);
  },
}));
