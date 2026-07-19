export interface ChatRequest {
  sessionId: string | null;
  connectionId: string | null;
  message: string;
}

export interface ChatResponse {
  sessionId: string;
  reply: string;
  suggestedFollowUps: string[];
  model: string;
  groundedAt: string | null;
  createdAt: string;
}

export interface ChatSessionSummary {
  id: string;
  connectionId: string | null;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessageDto {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  suggestedFollowUps: string[];
  model: string | null;
  createdAt: string;
}

export interface ChatSessionDetail {
  session: ChatSessionSummary;
  messages: ChatMessageDto[];
}
