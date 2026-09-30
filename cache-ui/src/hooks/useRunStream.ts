import { useEffect, useRef } from 'react';
import type { Sample } from '../types/api';
import { getLatestSample } from '../api/runApi';

/**
 * Subscribes to the SSE stream for a given runId and calls onSample
 * with each Sample event. Falls back to 500ms polling if SSE fails.
 * Cleans up on runId change or unmount.
 */
export function useRunStream(
  runId: string | null,
  onSample: (s: Sample) => void,
  onError: (msg: string) => void,
) {
  const onSampleRef = useRef(onSample);
  const onErrorRef = useRef(onError);
  useEffect(() => { onSampleRef.current = onSample; }, [onSample]);
  useEffect(() => { onErrorRef.current = onError; }, [onError]);

  useEffect(() => {
    if (!runId) return;

    let es: EventSource | null = null;
    let pollInterval: ReturnType<typeof setInterval> | null = null;
    let dead = false;
    const TERMINAL = new Set(['DONE', 'STOPPED', 'FAILED']);

    const startPolling = () => {
      if (pollInterval || dead) return;
      pollInterval = setInterval(async () => {
        if (dead) return;
        try {
          const s = await getLatestSample(runId);
          if (s) {
            onSampleRef.current(s);
            if (TERMINAL.has(s.status)) stop();
          }
        } catch { /* ignore transient errors */ }
      }, 500);
    };

    const stop = () => {
      dead = true;
      es?.close();
      if (pollInterval) clearInterval(pollInterval);
    };

    try {
      es = new EventSource(`/api/runs/${runId}/stream`);

      es.addEventListener('sample', (e: MessageEvent) => {
        if (dead) return;
        try {
          const s = JSON.parse(e.data) as Sample;
          onSampleRef.current(s);
          if (TERMINAL.has(s.status)) stop();
        } catch { /* malformed event */ }
      });

      es.onerror = () => {
        if (dead) return;
        es?.close();
        es = null;
        // Fall back to polling
        startPolling();
      };
    } catch {
      startPolling();
    }

    return stop;
  }, [runId]);
}
