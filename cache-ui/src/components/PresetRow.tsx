import React from 'react';
import type { RunConfig } from '../types/api';

interface Props {
  onSelect: (preset: Partial<RunConfig>) => void;
  onCompareSelect: (preset: Partial<RunConfig>) => void;
  disabled: boolean;
}

interface Preset {
  id: string;
  name: string;
  desc: string;
  config: Partial<RunConfig>;
  isCompare?: boolean;
}

const PRESETS: Preset[] = [
  {
    id: 'preset-zipf-lfu',
    name: 'Zipfian · LFU',
    desc: 'Skewed key popularity where frequency beats recency',
    config: {
      policy: 'LFU',
      pattern: 'ZIPFIAN',
      zipfSkew: 1.2,
      capacity: 100,
      keySpace: 1000,
      ttlMs: 0,
      totalOps: 200_000,
    },
  },
  {
    id: 'preset-scan',
    name: 'Scan Cache Polluter',
    desc: 'Sequential scan that flushes hot items out of LRU',
    config: {
      policy: 'LRU',
      pattern: 'SEQUENTIAL_SCAN',
      capacity: 100,
      keySpace: 1000,
      ttlMs: 0,
      totalOps: 200_000,
    },
  },
  {
    id: 'preset-ttl-churn',
    name: 'TTL Expiration Churn',
    desc: 'Short 500ms TTL causing frequent background expirations',
    config: {
      policy: 'LRU',
      pattern: 'ZIPFIAN',
      zipfSkew: 1.0,
      capacity: 100,
      keySpace: 1000,
      ttlMs: 500,
      totalOps: 200_000,
    },
  },
  {
    id: 'preset-compare-zipf',
    name: 'Compare: Zipfian',
    desc: 'Head-to-head LRU vs LFU under Zipfian distribution',
    config: {
      pattern: 'ZIPFIAN',
      zipfSkew: 1.2,
      capacity: 100,
      keySpace: 1000,
      ttlMs: 0,
      totalOps: 200_000,
    },
    isCompare: true,
  },
];

export default function PresetRow({ onSelect, onCompareSelect, disabled }: Props) {
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-ink-muted uppercase tracking-wider">
          Workload Presets
        </span>
        <span className="text-[11px] text-ink-muted">1-click demo setups</span>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
        {PRESETS.map((p) => (
          <button
            key={p.id}
            id={p.id}
            type="button"
            disabled={disabled}
            onClick={() => (p.isCompare ? onCompareSelect(p.config) : onSelect(p.config))}
            title={p.desc}
            className={`p-2.5 rounded-lg border text-left transition-all group ${
              disabled
                ? 'border-border bg-surface-muted opacity-50 cursor-not-allowed'
                : 'border-border bg-surface hover:border-accent hover:shadow-sm'
            }`}
          >
            <div className="text-xs font-semibold text-ink group-hover:text-accent truncate">
              {p.name}
            </div>
            <div className="text-[11px] text-ink-muted truncate mt-0.5">{p.desc}</div>
          </button>
        ))}
      </div>
    </div>
  );
}
