export type Policy = 'LRU' | 'LFU';
export type Pattern = 'ZIPFIAN' | 'SEQUENTIAL_SCAN' | 'UNIFORM' | 'HOT_SET_SHIFT';
export type Status = 'IDLE' | 'RUNNING' | 'DONE' | 'STOPPED' | 'FAILED';
export type AppStatus = Status;

export interface RunConfig {
  policy: Policy;
  capacity: number;
  keySpace: number;
  pattern: Pattern;
  zipfSkew: number;
  totalOps: number;
  threads: number;
  readRatio: number;
  ttlMs: number;
  loaderLatencyMs: number;
  seed: number;
}

export interface Sample {
  runId: string;
  status: Status;
  tMs: number;
  opsCompleted: number;
  totalOps: number;
  hits: number;
  misses: number;
  hitRate: number;
  missRate: number;
  windowHitRate: number;
  evictions: number;
  expirations: number;
  size: number;
  capacity: number;
  opsPerSec: number;
}

export interface CompareResponse {
  runIds: {
    LRU: string;
    LFU: string;
  };
}

export interface StartRunResponse {
  runId: string;
}

export interface ApiError {
  error: string;
}
