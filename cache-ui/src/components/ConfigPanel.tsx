import React from 'react';
import type { RunConfig, Policy, Pattern, AppStatus } from '../types/api';

interface Props {
  config: RunConfig;
  onChange: (c: RunConfig) => void;
  disabled: boolean;
  compareMode: boolean;
  onCompareToggle: () => void;
  onRun: () => void;
  inFlight: boolean;
  status: string;
}

const PATTERNS: { value: Pattern; label: string }[] = [
  { value: 'ZIPFIAN', label: 'Zipfian' },
  { value: 'SEQUENTIAL_SCAN', label: 'Sequential scan' },
  { value: 'UNIFORM', label: 'Uniform random' },
  { value: 'HOT_SET_SHIFT', label: 'Hot-set shift' },
];

function Field({
  id, label, helper, children,
}: { id: string; label: string; helper?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="block text-xs font-medium text-ink-muted uppercase tracking-wider">
        {label}
      </label>
      {children}
      {helper && <p className="text-xs text-ink-muted">{helper}</p>}
    </div>
  );
}

const inputCls = (disabled: boolean) =>
  `w-full h-9 px-3 rounded-lg border text-sm bg-muted transition-colors
   focus:outline-none focus:ring-2 focus:ring-accent
   ${disabled
     ? 'border-border text-ink-muted opacity-50 cursor-not-allowed'
     : 'border-border-strong text-ink hover:border-accent'
   }`;

export default function ConfigPanel({
  config, onChange, disabled, compareMode, onCompareToggle, onRun, inFlight, status,
}: Props) {
  const set = <K extends keyof RunConfig>(k: K, v: RunConfig[K]) =>
    onChange({ ...config, [k]: v });

  const showZipfSkew = config.pattern === 'ZIPFIAN' || config.pattern === 'HOT_SET_SHIFT';
  const isRunning = status === 'RUNNING';

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !isRunning && !inFlight) onRun();
    if (e.key === 'Escape' && isRunning) onRun();
  };

  return (
    <div className="space-y-5" onKeyDown={handleKeyDown}>
      <h2 className="text-sm font-semibold text-ink">Configuration</h2>

      {/* Policy segmented control */}
      <Field id="policy" label="Policy">
        <div
          role="group"
          aria-label="Cache eviction policy"
          className={`flex rounded-lg border overflow-hidden ${disabled || compareMode ? 'opacity-50' : 'border-border-strong'}`}
        >
          {(['LRU', 'LFU'] as Policy[]).map(p => (
            <button
              key={p}
              id={`policy-${p}`}
              type="button"
              disabled={disabled || compareMode}
              onClick={() => set('policy', p)}
              className={`flex-1 h-9 text-sm font-medium transition-colors
                ${config.policy === p && !compareMode
                  ? 'bg-accent text-accent-on'
                  : 'bg-muted text-ink-muted hover:text-ink'
                }
              `}
            >
              {p}
            </button>
          ))}
        </div>
        {compareMode && (
          <p className="text-xs text-ink-muted mt-1">Both policies run in compare mode</p>
        )}
      </Field>

      {/* Pattern */}
      <Field id="pattern" label="Access pattern">
        <select
          id="pattern"
          disabled={disabled}
          value={config.pattern}
          onChange={e => set('pattern', e.target.value as Pattern)}
          className={inputCls(disabled)}
        >
          {PATTERNS.map(p => (
            <option key={p.value} value={p.value}>{p.label}</option>
          ))}
        </select>
      </Field>

      {/* Zipf skew — only shown for relevant patterns */}
      {showZipfSkew && (
        <Field id="zipfSkew" label="Zipf skew" helper="Higher = more skewed toward hot keys">
          <input
            id="zipfSkew"
            type="number"
            min={0.1} max={3} step={0.1}
            disabled={disabled}
            value={config.zipfSkew}
            onChange={e => set('zipfSkew', parseFloat(e.target.value))}
            className={inputCls(disabled)}
          />
        </Field>
      )}

      {/* Capacity */}
      <Field id="capacity" label="Capacity" helper="Max entries in the cache">
        <input
          id="capacity"
          type="number"
          min={1} max={100_000}
          disabled={disabled}
          value={config.capacity}
          onChange={e => set('capacity', parseInt(e.target.value))}
          className={inputCls(disabled)}
        />
      </Field>

      {/* Key space */}
      <Field id="keySpace" label="Key space" helper="Total number of distinct keys">
        <input
          id="keySpace"
          type="number"
          min={1}
          disabled={disabled}
          value={config.keySpace}
          onChange={e => set('keySpace', parseInt(e.target.value))}
          className={inputCls(disabled)}
        />
      </Field>

      {/* Total ops */}
      <Field id="totalOps" label="Total ops">
        <input
          id="totalOps"
          type="number"
          min={1000}
          disabled={disabled}
          value={config.totalOps}
          onChange={e => set('totalOps', parseInt(e.target.value))}
          className={inputCls(disabled)}
        />
      </Field>

      {/* Threads */}
      <Field id="threads" label="Threads" helper="Concurrent workers hitting the cache">
        <input
          id="threads"
          type="number"
          min={1} max={64}
          disabled={disabled}
          value={config.threads}
          onChange={e => set('threads', parseInt(e.target.value))}
          className={inputCls(disabled)}
        />
      </Field>

      {/* Read ratio */}
      <Field id="readRatio" label="Read ratio" helper="Share of ops that are reads; misses trigger a load">
        <input
          id="readRatio"
          type="number"
          min={0} max={1} step={0.05}
          disabled={disabled}
          value={config.readRatio}
          onChange={e => set('readRatio', parseFloat(e.target.value))}
          className={inputCls(disabled)}
        />
      </Field>

      {/* TTL ms */}
      <Field id="ttlMs" label="TTL (ms)" helper="0 = no expiry">
        <input
          id="ttlMs"
          type="number"
          min={0}
          disabled={disabled}
          value={config.ttlMs}
          onChange={e => set('ttlMs', parseInt(e.target.value))}
          className={inputCls(disabled)}
        />
      </Field>

      {/* Loader latency */}
      <Field id="loaderLatencyMs" label="Loader latency (ms)" helper="Simulated backend fetch cost on cache miss">
        <input
          id="loaderLatencyMs"
          type="number"
          min={0}
          disabled={disabled}
          value={config.loaderLatencyMs}
          onChange={e => set('loaderLatencyMs', parseInt(e.target.value))}
          className={inputCls(disabled)}
        />
      </Field>

      {/* Seed */}
      <Field id="seed" label="Seed" helper="Deterministic trace seed">
        <input
          id="seed"
          type="number"
          disabled={disabled}
          value={config.seed}
          onChange={e => set('seed', parseInt(e.target.value))}
          className={inputCls(disabled)}
        />
      </Field>

      <div className="space-y-3 pt-1">
        {/* Run / Stop button */}
        <button
          id="run-btn"
          type="button"
          onClick={onRun}
          disabled={inFlight}
          className={`w-full h-10 rounded-lg font-semibold text-sm flex items-center justify-center gap-2 transition-all
            ${isRunning
              ? 'border border-border-strong bg-surface text-ink hover:bg-muted'
              : 'bg-accent text-accent-on hover:bg-accent-hover'
            }
            ${inFlight ? 'opacity-50 cursor-not-allowed' : ''}
          `}
        >
          {isRunning ? (
            <>
              <span className="w-3 h-3 border border-current rounded-sm inline-block" aria-hidden="true" />
              Stop
            </>
          ) : (
            <>
              <span className="border-l-[6px] border-l-current border-y-[5px] border-y-transparent inline-block" aria-hidden="true" />
              {inFlight ? 'Starting…' : 'Run'}
            </>
          )}
        </button>

        {/* Compare toggle */}
        <label className="flex items-center gap-2.5 cursor-pointer select-none">
          <button
            id="compare-toggle"
            role="switch"
            aria-checked={compareMode}
            type="button"
            disabled={isRunning}
            onClick={onCompareToggle}
            className={`relative w-9 h-5 rounded-full transition-colors
              ${compareMode ? 'bg-accent' : 'bg-muted border border-border-strong'}
              ${isRunning ? 'opacity-50 cursor-not-allowed' : ''}
            `}
          >
            <span
              className={`absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white shadow-sm transition-transform
                ${compareMode ? 'translate-x-4' : 'translate-x-0'}
              `}
            />
          </button>
          <span className="text-sm text-ink-muted">Compare LRU vs LFU</span>
        </label>
      </div>
    </div>
  );
}
