export interface SnapshotSummary {
  id: string;
  connectionId: string;
  capturedAt: string;
  dbSizeBytes: number;
  activeSessions: number;
  idleInTransaction: number;
  blockedSessions: number;
  waitingLocks: number;
  cacheHitRatio: number | null;
  xactCommit: number;
  xactRollback: number;
  deadlocks: number;
  tempBytes: number;
}

export interface QueryStat {
  queryId: string;
  query: string;
  calls: number;
  totalTimeMs: number;
  meanTimeMs: number;
  rows: number;
  sharedBlksHit: number;
  sharedBlksRead: number;
  hitRatio: number;
}

export interface SessionInfo {
  pid: number;
  user: string;
  state: string;
  waitEventType: string | null;
  waitEvent: string | null;
  durationSeconds: number | null;
  query: string;
}

export interface LockInfo {
  blockedPid: number;
  blockedQuery: string;
  blockingPid: number;
  blockingQuery: string;
}

export interface TableStat {
  tableName: string;
  seqScans: number;
  seqTupRead: number;
  idxScans: number;
  liveRows: number;
}

export interface SnapshotDetail {
  summary: SnapshotSummary;
  topQueries: QueryStat[];
  sessions: SessionInfo[];
  locks: LockInfo[];
  tableStats: TableStat[];
}

export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  return `${(bytes / 1024 ** i).toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}
