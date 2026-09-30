import React, { useState } from 'react';

export default function App() {
  const [theme, setTheme] = useState<'light' | 'dark'>('light');

  const toggleTheme = () => {
    const next = theme === 'light' ? 'dark' : 'light';
    setTheme(next);
    if (next === 'dark') {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  };

  return (
    <div className="min-h-screen bg-bg text-ink transition-colors duration-150">
      <header className="h-14 border-b border-border bg-surface px-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-6 h-6 rounded-md bg-accent flex items-center justify-center text-white font-bold text-xs">
            ◧
          </div>
          <h1 className="text-base font-semibold tracking-tight text-ink">CacheLab</h1>
        </div>
        <div className="flex items-center gap-4">
          <button
            onClick={toggleTheme}
            className="p-1.5 rounded-field border border-border bg-muted text-ink-muted hover:text-ink text-xs font-medium"
          >
            {theme === 'light' ? '☾ Dark' : '☼ Light'}
          </button>
        </div>
      </header>
      <main className="p-6">
        <p className="text-ink-muted text-sm">CacheLab Initializing...</p>
      </main>
    </div>
  );
}
