import client from './client';
import type { AnalysisHistoryItem, QueryAnalysisResponse } from '../types/analyzer';

export interface AnalyzePayload {
  connectionId: string | null;
  sql: string | null;
  explainOutput: string | null;
  runAnalyze: boolean;
}

export const analyzerApi = {
  analyze: (payload: AnalyzePayload) =>
    // AI analysis with thinking can take a while — allow up to 3 minutes
    client.post<QueryAnalysisResponse>('/analyzer/analyze', payload, { timeout: 180_000 }).then((r) => r.data),

  history: (limit = 25) =>
    client.get<AnalysisHistoryItem[]>('/analyzer/history', { params: { limit } }).then((r) => r.data),

  get: (id: string) => client.get<QueryAnalysisResponse>(`/analyzer/${id}`).then((r) => r.data),
};
