import { RunConfig, Sample, StartRunResponse, CompareResponse } from '../types/api';

const BASE = '/api';

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const json = await res.json();
  if (!res.ok) {
    throw new Error((json as { error?: string }).error ?? `HTTP ${res.status}`);
  }
  return json as T;
}

async function del<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`, { method: 'DELETE' });
  if (!res.ok) {
    const json = await res.json().catch(() => ({}));
    throw new Error((json as { error?: string }).error ?? `HTTP ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export function startRun(config: RunConfig): Promise<StartRunResponse> {
  return post<StartRunResponse>('/runs', config);
}

export function stopRun(runId: string): Promise<void> {
  return del<void>(`/runs/${runId}`);
}

export interface CompareRequest {
  capacity?: number;
  keySpace?: number;
  pattern?: string;
  zipfSkew?: number;
  totalOps?: number;
  threads?: number;
  readRatio?: number;
  ttlMs?: number;
  loaderLatencyMs?: number;
  seed?: number;
}

export function startCompare(req: CompareRequest): Promise<CompareResponse> {
  return post<CompareResponse>('/runs/compare', req);
}

export async function getLatestSample(runId: string): Promise<Sample | null> {
  const res = await fetch(`${BASE}/runs/${runId}/latest`);
  if (res.status === 404) return null;
  return res.json() as Promise<Sample>;
}
