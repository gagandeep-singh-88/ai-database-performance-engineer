import client from './client';
import type { SnapshotDetail, SnapshotSummary } from '../types/metrics';

export const metricsApi = {
  collectNow: (connectionId: string) =>
    client.post<SnapshotDetail>(`/connections/${connectionId}/snapshots`).then((r) => r.data),

  history: (connectionId: string, limit = 50) =>
    client
      .get<SnapshotSummary[]>(`/connections/${connectionId}/snapshots`, { params: { limit } })
      .then((r) => r.data),

  latest: (connectionId: string) =>
    client.get<SnapshotDetail>(`/connections/${connectionId}/snapshots/latest`).then((r) => r.data),
};
