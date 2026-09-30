/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        bg: 'var(--bg)',
        surface: 'var(--surface)',
        muted: 'var(--surface-muted)',
        border: 'var(--border)',
        'border-strong': 'var(--border-strong)',
        ink: 'var(--text)',
        'ink-muted': 'var(--text-muted)',
        'ink-subtle': 'var(--text-subtle)',
        accent: {
          DEFAULT: 'var(--accent)',
          hover: 'var(--accent-hover)',
          soft: 'var(--accent-soft)',
          on: 'var(--on-accent)',
        },
        hit: {
          DEFAULT: 'var(--hit)',
          soft: 'var(--hit-soft)',
        },
        miss: {
          DEFAULT: 'var(--miss)',
          soft: 'var(--miss-soft)',
        },
        evict: {
          DEFAULT: 'var(--evict)',
          soft: 'var(--evict-soft)',
        },
        expire: {
          DEFAULT: 'var(--expire)',
          soft: 'var(--expire-soft)',
        },
        series: {
          lru: 'var(--series-lru)',
          lfu: 'var(--series-lfu)',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        card: '12px',
        field: '8px',
      },
    },
  },
  plugins: [],
};
