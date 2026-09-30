import React from 'react';
import type { Sample } from '../types/api';

interface Props {
  sample: Sample | null;
}

interface CounterItemProps {
  id: string;
  label: string;
  value: string;
  dotColor?: string;
  sublabel?: string;
}

function CounterItem({ id, label, value, dotColor, sublabel }: CounterItemProps) {
  return (
    <div
      id={id}
      className="rounded-xl border border-border bg-surface p-3.5 shadow-sm space-y-1 transition-colors"
    >
      <div className="flex items-center gap-1.5">
        {dotColor && (
          <span
            className="w-2 h-2 rounded-full inline-block shrink-0"
            style={{ backgroundColor: dotColor }}
            aria-hidden="true"
          />
        )}
        <span className="text-xs font-medium text-ink-muted uppercase tracking-wider truncate">
          {label}
        </span>
      </div>
      <div className="text-lg font-bold tabular-nums text-ink">{value}</div>
      {sublabel && <div className="text-[11px] text-ink-muted">{sublabel}</div>}
    </div>
  );
}

export default function CounterGrid({ sample }: Props) {
  const hits = sample ? sample.hits.toLocaleString() : '—';
  const misses = sample ? sample.misses.toLocaleString() : '—';
  const evictions = sample ? sample.evictions.toLocaleString() : '—';
  const expirations = sample ? sample.expirations.toLocaleString() : '—';
  const occupancy = sample
    ? `${sample.size.toLocaleString()} / ${sample.capacity.toLocaleString()}`
    : '—';
  const occupancyPct = sample && sample.capacity > 0
    ? `(${((sample.size / sample.capacity) * 100).toFixed(0)}% full)`
    : undefined;

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
      <CounterItem
        id="counter-hits"
        label="Cache Hits"
        value={hits}
        dotColor="var(--hit)"
      />
      <CounterItem
        id="counter-misses"
        label="Cache Misses"
        value={misses}
        dotColor="var(--miss)"
      />
      <CounterItem
        id="counter-evictions"
        label="Evictions"
        value={evictions}
        dotColor="var(--evict)"
      />
      <CounterItem
        id="counter-expirations"
        label="TTL Expirations"
        value={expirations}
        dotColor="var(--expire)"
      />
      <CounterItem
        id="counter-size"
        label="Size / Cap"
        value={occupancy}
        sublabel={occupancyPct}
      />
    </div>
  );
}
