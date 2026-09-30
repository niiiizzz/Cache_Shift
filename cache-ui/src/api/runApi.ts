import { RunConfig, Sample, StartRunResponse, CompareResponse } from '../types/api';

const BASE = '/api';

async function post<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  const text = await res.text();
  let json: Record<string, unknown> = {};
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    if (!res.ok) {
      throw new Error(`Server returned HTTP ${res.status}: ${text || res.statusText}`);
    }
  }

  if (!res.ok) {
    const errMsg = (json as { error?: string }).error || (json as { message?: string }).message || `HTTP ${res.status} (${res.statusText})`;
    throw new Error(errMsg);
  }

  return json as T;
}

export function startRun(config: RunConfig): Promise<StartRunResponse> {
  return post<StartRunResponse>('/runs', config);
}

export function stopRun(runId: string): Promise<void> {
  return post<void>(`/runs/${runId}/stop`);
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
  return post<CompareResponse>('/compare', req);
}

export async function getLatestSample(runId: string): Promise<Sample | null> {
  const res = await fetch(`${BASE}/runs/${runId}`);
  if (res.status === 404) return null;
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text) as Sample;
  } catch {
    return null;
  }
}
