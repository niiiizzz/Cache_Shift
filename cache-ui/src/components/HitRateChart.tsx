import React from 'react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  CartesianGrid,
} from 'recharts';
import type { Sample } from '../types/api';

interface Props {
  samples: Sample[];
  compareSamples?: Sample[];
  isIdle: boolean;
  compareMode: boolean;
  latestHitRate?: number;
}

interface TooltipPayloadItem {
  name: string;
  value: number;
  color: string;
}

interface CustomTooltipProps {
  active?: boolean;
  payload?: TooltipPayloadItem[];
  label?: number;
}

function CustomTooltip({ active, payload, label }: CustomTooltipProps) {
  if (!active || !payload || !payload.length) return null;
  return (
    <div className="rounded-lg border border-border bg-surface p-2.5 shadow-lg text-xs space-y-1">
      <p className="font-semibold text-ink-muted">Elapsed: {label}s</p>
      {payload.map((entry, idx) => (
        <div key={idx} className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: entry.color }} />
          <span className="text-ink-muted font-medium">{entry.name}:</span>
          <span className="font-bold tabular-nums text-ink">{entry.value}%</span>
        </div>
      ))}
    </div>
  );
}

export default function HitRateChart({
  samples,
  compareSamples,
  isIdle,
  compareMode,
  latestHitRate,
}: Props) {
  if (isIdle || samples.length === 0) {
    return (
      <div
        className="h-80 w-full rounded-xl border border-border bg-surface flex flex-col items-center justify-center p-6 text-center"
        aria-label="Empty chart area"
      >
        <div className="w-12 h-12 rounded-full bg-surface-muted flex items-center justify-center text-ink-muted mb-3 text-xl">
          📈
        </div>
        <p className="text-sm font-medium text-ink">Ready to simulate</p>
        <p className="text-xs text-ink-muted mt-1 max-w-sm">
          Pick a workload configuration on the left and click <strong>Run</strong> to stream real-time hit rate and eviction metrics.
        </p>
      </div>
    );
  }

  const baseTime = samples[0]?.tMs ?? 0;
  const chartData = samples.map((s, idx) => {
    const elapsed = Math.max(0, (s.tMs - baseTime) / 1000);
    const cs = compareSamples?.[idx];
    return {
      tSec: Number(elapsed.toFixed(1)),
      hitRate: Number((s.hitRate * 100).toFixed(1)),
      missRate: Number((s.missRate * 100).toFixed(1)),
      windowHitRate: Number((s.windowHitRate * 100).toFixed(1)),
      lruHitRate: Number((s.hitRate * 100).toFixed(1)),
      lfuHitRate: cs ? Number((cs.hitRate * 100).toFixed(1)) : undefined,
    };
  });

  return (
    <div
      className="rounded-xl border border-border bg-surface p-4 shadow-sm space-y-2"
      aria-label={
        latestHitRate !== undefined
          ? `Hit rate chart, current hit rate ${(latestHitRate * 100).toFixed(1)}%`
          : 'Hit rate chart'
      }
    >
      <div className="flex items-center justify-between px-1">
        <h3 className="text-xs font-semibold text-ink-muted uppercase tracking-wider">
          {compareMode ? 'Policy Comparison (Hit Rate %)' : 'Hit Rate & Miss Rate Over Time'}
        </h3>
        <span className="text-xs tabular-nums text-ink-muted">
          {samples.length} samples
        </span>
      </div>

      <div className="h-72 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={chartData} margin={{ top: 10, right: 15, left: -15, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" opacity={0.6} />
            <XAxis
              dataKey="tSec"
              tickFormatter={(v: number) => `${v}s`}
              stroke="var(--text-subtle)"
              fontSize={11}
              tickLine={false}
            />
            <YAxis
              domain={[0, 100]}
              ticks={[0, 25, 50, 75, 100]}
              tickFormatter={(v: number) => `${v}%`}
              stroke="var(--text-subtle)"
              fontSize={11}
              tickLine={false}
            />
            <Tooltip content={<CustomTooltip />} />
            <Legend
              wrapperStyle={{ fontSize: '12px', paddingTop: '8px' }}
              iconType="circle"
              iconSize={8}
            />

            {compareMode ? (
              <>
                <Line
                  type="monotone"
                  dataKey="lruHitRate"
                  name="LRU Hit Rate"
                  stroke="var(--series-lru)"
                  strokeWidth={2.5}
                  dot={false}
                  isAnimationActive={false}
                />
                <Line
                  type="monotone"
                  dataKey="lfuHitRate"
                  name="LFU Hit Rate"
                  stroke="var(--series-lfu)"
                  strokeWidth={2.5}
                  dot={false}
                  isAnimationActive={false}
                />
              </>
            ) : (
              <>
                <Line
                  type="monotone"
                  dataKey="hitRate"
                  name="Cumulative Hit Rate"
                  stroke="var(--hit)"
                  strokeWidth={2.5}
                  dot={false}
                  isAnimationActive={false}
                />
                <Line
                  type="monotone"
                  dataKey="missRate"
                  name="Miss Rate"
                  stroke="var(--miss)"
                  strokeWidth={1.5}
                  strokeDasharray="4 4"
                  dot={false}
                  isAnimationActive={false}
                />
                <Line
                  type="monotone"
                  dataKey="windowHitRate"
                  name="Window Hit Rate"
                  stroke="var(--accent)"
                  strokeWidth={1.5}
                  dot={false}
                  isAnimationActive={false}
                  opacity={0.7}
                />
              </>
            )}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
