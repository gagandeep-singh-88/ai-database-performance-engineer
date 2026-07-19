export interface AiIssue {
  severity: 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO' | string;
  type: string;
  description: string;
}

export interface AiRecommendation {
  title: string;
  detail: string;
  sql: string | null;
  estimatedImprovement: string | null;
}

export interface AiQueryAnalysis {
  summary: string;
  issues: AiIssue[];
  recommendations: AiRecommendation[];
  optimizedSql: string | null;
  estimatedImprovement: string | null;
  planExplanation: string | null;
}

export interface QueryAnalysisResponse {
  id: string;
  connectionId: string | null;
  sql: string | null;
  planUsed: string | null;
  schemaContext: string | null;
  analysis: AiQueryAnalysis;
  model: string;
  createdAt: string;
}

export interface AnalysisHistoryItem {
  id: string;
  connectionId: string | null;
  sqlSnippet: string;
  summary: string | null;
  model: string;
  createdAt: string;
}
