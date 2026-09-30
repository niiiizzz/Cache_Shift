import React, { useCallback, useEffect, useRef, useState } from 'react';
import type { Policy, Pattern, RunConfig, Sample, Status } from './types/api';
import ConfigPanel from './components/ConfigPanel';
import HitRateChart from './components/HitRateChart';
import KpiCard from './components/KpiCard';
import CounterGrid from './components/CounterGrid';
import ProgressBar from './components/ProgressBar';
import VerdictBanner from './components/VerdictBanner';
import ErrorBanner from './components/ErrorBanner';
import PresetRow from './components/PresetRow';
import { useRunStream } from './hooks/useRunStream';
import { startRun, stopRun, startCompare } from './api/runApi';

const DEFAULT_CONFIG: RunConfig = {
  policy: 'LRU',
  capacity: 100,
  keySpace: 1000,
  pattern: 'ZIPFIAN',
  zipfSkew: 1.0,
  totalOps: 200_000,
  threads: 8,
  readRatio: 0.9,
  ttlMs: 0,
  loaderLatencyMs: 1,
  seed: 42,
};

type AppStatus = 'IDLE' | 'RUNNING' | 'DONE' | 'STOPPED' | 'FAILED';

interface RunState {
  runId: string | null;
  compareRunId: string | null; // LFU run in compare mode
  status: AppStatus;
  samples: Sample[];
  compareSamples: Sample[]; // For LFU in compare mode
  latestSample: Sample | null;
  latestCompareSample: Sample | null;
  errorMessage: string | null;
  startTime: number | null;
}

const INITIAL_RUN_STATE: RunState = {
  runId: null,
  compareRunId: null,
  status: 'IDLE',
  samples: [],
  compareSamples: [],
  latestSample: null,
  latestCompareSample: null,
  errorMessage: null,
  startTime: null,
};

export default function App() {
  const [theme, setTheme] = useState<'light' | 'dark'>('dark');
  const [config, setConfig] = useState<RunConfig>(DEFAULT_CONFIG);
  const [compareMode, setCompareMode] = useState(false);
  const [runState, setRunState] = useState<RunState>(INITIAL_RUN_STATE);
  const [inFlight, setInFlight] = useState(false);
  const stopRequested = useRef(false);

  // Apply initial dark theme
  useEffect(() => {
    document.documentElement.classList.add('dark');
  }, []);

  const toggleTheme = () => {
    const next = theme === 'light' ? 'dark' : 'light';
    setTheme(next);
    if (next === 'dark') {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  };

  const onSample = useCallback((sample: Sample) => {
    if (stopRequested.current && sample.status !== 'RUNNING') return;
    setRunState(prev => {
      const isCompare = sample.runId === prev.compareRunId;
      if (isCompare) {
        const newSamples = [...prev.compareSamples, sample].slice(-300);
        return {
          ...prev,
          compareSamples: newSamples,
          latestCompareSample: sample,
          status: sample.status === 'DONE' && (prev.latestSample?.status === 'DONE') ? 'DONE' : prev.status,
        };
      } else {
        const newSamples = [...prev.samples, sample].slice(-300);
        const newStatus: AppStatus =
          sample.status === 'DONE' ? (compareMode && prev.latestCompareSample?.status !== 'DONE' ? 'RUNNING' : 'DONE')
          : sample.status === 'STOPPED' ? 'STOPPED'
          : sample.status === 'FAILED' ? 'FAILED'
          : 'RUNNING';
        return {
          ...prev,
          samples: newSamples,
          latestSample: sample,
          status: newStatus,
          errorMessage: sample.status === 'FAILED' ? 'Simulation failed.' : prev.errorMessage,
        };
      }
    });
  }, [compareMode]);

  const onError = useCallback((msg: string) => {
    setRunState(prev => ({ ...prev, status: 'FAILED', errorMessage: msg }));
  }, []);

  useRunStream(runState.runId, onSample, onError);
  useRunStream(runState.compareRunId, onSample, onError);

  const handleRun = async () => {
    if (runState.status === 'RUNNING') {
      // Stop
      stopRequested.current = true;
      setInFlight(true);
      try {
        if (runState.runId) await stopRun(runState.runId);
        if (runState.compareRunId) await stopRun(runState.compareRunId);
        setRunState(prev => ({ ...prev, status: 'STOPPED' }));
      } catch {
        // Optimistic — server may already have stopped
      } finally {
        setInFlight(false);
      }
      return;
    }

    // Start
    stopRequested.current = false;
    setInFlight(true);
    setRunState({ ...INITIAL_RUN_STATE, startTime: Date.now(), status: 'RUNNING' });
    try {
      if (compareMode) {
        const resp = await startCompare({
          capacity: config.capacity,
          keySpace: config.keySpace,
          pattern: config.pattern,
          zipfSkew: config.zipfSkew,
          totalOps: config.totalOps,
          threads: config.threads,
          readRatio: config.readRatio,
          ttlMs: config.ttlMs,
          loaderLatencyMs: config.loaderLatencyMs,
          seed: config.seed,
        });
        setRunState(prev => ({
          ...prev,
          runId: resp.runIds.LRU,
          compareRunId: resp.runIds.LFU,
        }));
      } else {
        const resp = await startRun(config);
        setRunState(prev => ({ ...prev, runId: resp.runId }));
      }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Failed to start run';
      setRunState(prev => ({ ...prev, status: 'FAILED', errorMessage: msg }));
    } finally {
      setInFlight(false);
    }
  };

  const isRunning = runState.status === 'RUNNING';
  const isIdle = runState.status === 'IDLE';
  const s = runState.latestSample;
  const cs = runState.latestCompareSample;

  const elapsedS = runState.startTime && !isIdle
    ? ((s?.tMs ?? Date.now() - runState.startTime!) / 1000).toFixed(1)
    : null;

  const statusLabel: Record<AppStatus, string> = {
    IDLE: 'Idle',
    RUNNING: 'Running',
    DONE: 'Done',
    STOPPED: 'Stopped',
    FAILED: 'Failed',
  };

  const statusColors: Record<AppStatus, string> = {
    IDLE: 'bg-[var(--surface-muted)] text-[var(--text-muted)]',
    RUNNING: 'bg-[var(--accent-soft)] text-[var(--accent)]',
    DONE: 'bg-[var(--hit-soft)] text-[var(--hit)]',
    STOPPED: 'bg-[var(--evict-soft)] text-[var(--evict)]',
    FAILED: 'bg-[var(--miss-soft)] text-[var(--miss)]',
  };

  return (
    <div className="min-h-screen bg-bg text-ink font-sans transition-colors duration-150">
      {/* Header */}
      <header className="h-14 border-b border-border bg-surface px-6 flex items-center justify-between sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <div className="w-7 h-7 rounded-lg bg-accent flex items-center justify-center text-white font-bold text-sm select-none">◧</div>
          <h1 className="text-base font-semibold tracking-tight text-ink">CacheLab</h1>
        </div>
        <div className="flex items-center gap-3">
          <span
            aria-live="polite"
            className={`px-3 py-1 rounded-full text-xs font-semibold flex items-center gap-1.5 transition-all ${statusColors[runState.status]}`}
          >
            {isRunning && (
              <span className="w-1.5 h-1.5 rounded-full bg-accent animate-pulse" aria-hidden="true" />
            )}
            {statusLabel[runState.status]}
          </span>
          <button
            id="theme-toggle"
            onClick={toggleTheme}
            className="px-3 py-1.5 rounded-lg border border-border bg-surface-muted text-ink-muted hover:text-ink text-xs font-medium transition-colors"
          >
            {theme === 'light' ? '☾ Dark' : '☼ Light'}
          </button>
        </div>
      </header>

      {/* Main layout */}
      <div className="flex flex-col lg:flex-row min-h-[calc(100vh-56px)]">
        {/* Config panel */}
        <aside className="w-full lg:w-80 shrink-0 border-b lg:border-b-0 lg:border-r border-border bg-surface p-5 space-y-5 lg:sticky lg:top-14 lg:h-[calc(100vh-56px)] lg:overflow-y-auto">
          <ConfigPanel
            config={config}
            onChange={setConfig}
            disabled={isRunning}
            compareMode={compareMode}
            onCompareToggle={() => setCompareMode(v => !v)}
            onRun={handleRun}
            inFlight={inFlight}
            status={runState.status}
          />
        </aside>

        {/* Results panel */}
        <main className="flex-1 p-5 lg:p-6 space-y-5 max-w-5xl">
          {/* Error banner */}
          {runState.errorMessage && (
            <ErrorBanner
              message={runState.errorMessage}
              onDismiss={() => setRunState(prev => ({ ...prev, errorMessage: null }))}
            />
          )}

          {/* Presets */}
          <PresetRow
            onSelect={(preset) => setConfig({ ...config, ...preset })}
            onCompareSelect={(preset) => {
              setConfig({ ...config, ...preset });
              setCompareMode(true);
            }}
            disabled={isRunning}
          />

          {/* KPI cards */}
          <div className="grid grid-cols-3 gap-4">
            <KpiCard
              id="kpi-hit-rate"
              label="Hit rate"
              helper="Share of reads served from cache"
              value={s ? `${(s.hitRate * 100).toFixed(1)}%` : '—'}
              caption={s ? `Last window: ${(s.windowHitRate * 100).toFixed(1)}%` : undefined}
              color="hit"
            />
            <KpiCard
              id="kpi-miss-rate"
              label="Miss rate"
              helper="Includes reads of expired entries"
              value={s ? `${(s.missRate * 100).toFixed(1)}%` : '—'}
              color="miss"
            />
            <KpiCard
              id="kpi-ops-sec"
              label="Ops / sec"
              helper="Throughput across all threads"
              value={s ? s.opsPerSec.toLocaleString() : '—'}
              color="neutral"
            />
          </div>

          {/* Chart */}
          <HitRateChart
            samples={runState.samples}
            compareSamples={compareMode ? runState.compareSamples : undefined}
            isIdle={isIdle}
            compareMode={compareMode}
            latestHitRate={s?.hitRate}
          />

          {/* Verdict banner (compare mode) */}
          {compareMode && runState.status === 'DONE' && s && cs && (
            <VerdictBanner lruSample={s} lfuSample={cs} />
          )}

          {/* Counter grid */}
          <CounterGrid sample={s} />

          {/* Progress bar */}
          {s && (
            <ProgressBar
              opsCompleted={s.opsCompleted}
              totalOps={s.totalOps}
              status={runState.status}
              elapsedS={elapsedS}
            />
          )}

          {/* Done summary */}
          {runState.status === 'DONE' && s && elapsedS && (
            <p className="text-xs text-ink-muted text-center">
              Finished in {elapsedS} s · {s.opsCompleted.toLocaleString()} ops · {s.opsPerSec.toLocaleString()} ops/s
            </p>
          )}
        </main>
      </div>
    </div>
  );
}
