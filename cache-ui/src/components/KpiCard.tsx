import React from 'react';

interface Props {
  id: string;
  label: string;
  helper?: string;
  value: string;
  caption?: string;
  color: 'hit' | 'miss' | 'neutral';
}

const colorMap = {
  hit: 'text-[var(--hit)]',
  miss: 'text-[var(--miss)]',
  neutral: 'text-ink',
};

export default function KpiCard({ id, label, helper, value, caption, color }: Props) {
  return (
    <div
      id={id}
      className="bg-surface rounded-card border border-border p-4 space-y-1"
      style={{ boxShadow: '0 1px 2px rgba(0,0,0,0.04)' }}
    >
      <p className="text-xs font-semibold uppercase tracking-[0.04em] text-ink-muted">{label}</p>
      <p
        className={`text-4xl font-semibold tabular-nums leading-none ${colorMap[color]}`}
        aria-label={`${label}: ${value}`}
      >
        {value}
      </p>
      {helper && <p className="text-xs text-ink-muted">{helper}</p>}
      {caption && <p className="text-xs text-ink-subtle">{caption}</p>}
    </div>
  );
}
