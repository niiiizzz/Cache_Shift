import React from 'react';
import type { Sample } from '../types/api';

interface Props {
  lruSample: Sample;
  lfuSample: Sample;
}

export default function VerdictBanner({ lruSample, lfuSample }: Props) {
  const lruHitPct = lruSample.hitRate * 100;
  const lfuHitPct = lfuSample.hitRate * 100;
  const delta = lfuHitPct - lruHitPct;
  const absDelta = Math.abs(delta);

  let winner: 'LFU' | 'LRU' | 'TIE' = 'TIE';
  let message = '';

  if (absDelta < 0.5) {
    winner = 'TIE';
    message = `Both policies performed equivalently (within 0.5% hit rate difference).`;
  } else if (delta > 0) {
    winner = 'LFU';
    message = `LFU achieved a higher hit rate by +${absDelta.toFixed(1)} percentage points over LRU.`;
  } else {
    winner = 'LRU';
    message = `LRU achieved a higher hit rate by +${absDelta.toFixed(1)} percentage points over LFU.`;
  }

  return (
    <div
      id="verdict-banner"
      className="rounded-xl border border-border bg-surface p-4 shadow-sm space-y-3"
      role="region"
      aria-label="Comparison verdict"
    >
      <div className="flex items-center gap-2">
        <span className="text-base" aria-hidden="true">
          🏆
        </span>
        <h3 className="text-sm font-bold text-ink">
          Comparison Verdict:{' '}
          {winner === 'TIE' ? (
            <span className="text-ink-muted">Tie / Marginal Difference</span>
          ) : (
            <span
              style={{
                color: winner === 'LRU' ? 'var(--series-lru)' : 'var(--series-lfu)',
              }}
            >
              {winner} Wins
            </span>
          )}
        </h3>
      </div>

      <p className="text-xs text-ink-muted">{message}</p>

      <div className="grid grid-cols-2 gap-3 pt-1">
        <div className="rounded-lg border border-border bg-surface-muted p-3 space-y-1">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold" style={{ color: 'var(--series-lru)' }}>
              LRU Final
            </span>
            <span className="text-xs font-bold tabular-nums text-ink">
              {lruHitPct.toFixed(1)}% Hit
            </span>
          </div>
          <div className="text-[11px] text-ink-muted tabular-nums">
            {lruSample.hits.toLocaleString()} hits · {lruSample.evictions.toLocaleString()} evictions
          </div>
        </div>

        <div className="rounded-lg border border-border bg-surface-muted p-3 space-y-1">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold" style={{ color: 'var(--series-lfu)' }}>
              LFU Final
            </span>
            <span className="text-xs font-bold tabular-nums text-ink">
              {lfuHitPct.toFixed(1)}% Hit
            </span>
          </div>
          <div className="text-[11px] text-ink-muted tabular-nums">
            {lfuSample.hits.toLocaleString()} hits · {lfuSample.evictions.toLocaleString()} evictions
          </div>
        </div>
      </div>
    </div>
  );
}
