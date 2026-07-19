import client from './client';
import type { DashboardResponse, Recommendation } from '../types/dashboard';

export const dashboardApi = {
  get: (connectionId: string, historyLimit = 100) =>
    client
      .get<DashboardResponse>(`/connections/${connectionId}/dashboard`, { params: { historyLimit } })
      .then((r) => r.data),

  aiRecommendations: (connectionId: string) =>
    client
      .post<Recommendation[]>(`/connections/${connectionId}/dashboard/ai-recommendations`, null, {
        timeout: 180_000,
      })
      .then((r) => r.data),
};
