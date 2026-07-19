import type { SnapshotDetail, SnapshotSummary } from './metrics';

export interface ScoreFactor {
  label: string;
  penalty: number;
}

export interface Recommendation {
  severity: 'HIGH' | 'MEDIUM' | 'LOW' | string;
  title: string;
  detail: string;
  sql: string | null;
}

export interface DashboardResponse {
  hasData: boolean;
  healthScore: number;
  grade: string;
  factors: ScoreFactor[];
  recommendations: Recommendation[];
  latest: SnapshotDetail | null;
  history: SnapshotSummary[];
}
