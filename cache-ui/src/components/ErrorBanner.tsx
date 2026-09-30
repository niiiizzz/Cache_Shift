import React from 'react';

interface Props {
  message: string;
  onDismiss: () => void;
}

export default function ErrorBanner({ message, onDismiss }: Props) {
  return (
    <div
      role="alert"
      className="rounded-xl border border-[var(--miss)] bg-[var(--miss-soft)] p-3.5 flex items-center justify-between text-xs text-[var(--miss)] shadow-sm"
    >
      <div className="flex items-center gap-2">
        <span className="font-bold text-sm" aria-hidden="true">
          ⚠
        </span>
        <span className="font-medium">{message}</span>
      </div>
      <button
        type="button"
        onClick={onDismiss}
        className="text-xs font-semibold px-2 py-1 rounded hover:bg-black/10 dark:hover:bg-white/10 transition-colors"
        aria-label="Dismiss error"
      >
        ✕
      </button>
    </div>
  );
}
