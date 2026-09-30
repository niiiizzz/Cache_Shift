import React from 'react';

interface Props {
  opsCompleted: number;
  totalOps: number;
  status: string;
  elapsedS: string | null;
}

export default function ProgressBar({
  opsCompleted,
  totalOps,
  status,
  elapsedS,
}: Props) {
  const pct = totalOps > 0 ? Math.min(100, Math.max(0, (opsCompleted / totalOps) * 100)) : 0;
  const isRunning = status === 'RUNNING';

  return (
    <div className="rounded-xl border border-border bg-surface p-3.5 shadow-sm space-y-2">
      <div className="flex items-center justify-between text-xs">
        <span className="font-semibold text-ink-muted uppercase tracking-wider">
          Simulation Progress
        </span>
        <span className="tabular-nums font-medium text-ink">
          {opsCompleted.toLocaleString()} / {totalOps.toLocaleString()} ops ({pct.toFixed(1)}%)
          {elapsedS && <span className="text-ink-muted ml-2">· {elapsedS}s elapsed</span>}
        </span>
      </div>

      <div
        className="w-full h-2 bg-surface-muted rounded-full overflow-hidden"
        role="progressbar"
        aria-valuenow={opsCompleted}
        aria-valuemin={0}
        aria-valuemax={totalOps}
        aria-label="Simulation progress bar"
      >
        <div
          className={`h-full transition-all duration-150 rounded-full ${
            isRunning ? 'bg-accent' : pct >= 100 ? 'bg-[var(--hit)]' : 'bg-accent'
          }`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}
