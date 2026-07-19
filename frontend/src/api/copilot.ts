import client from './client';
import type { ChatRequest, ChatResponse, ChatSessionDetail, ChatSessionSummary } from '../types/copilot';

export const copilotApi = {
  chat: (payload: ChatRequest) => client.post<ChatResponse>('/copilot/chat', payload).then((r) => r.data),

  sessions: (limit = 25) =>
    client.get<ChatSessionSummary[]>('/copilot/sessions', { params: { limit } }).then((r) => r.data),

  session: (id: string) => client.get<ChatSessionDetail>(`/copilot/sessions/${id}`).then((r) => r.data),

  remove: (id: string) => client.delete(`/copilot/sessions/${id}`),
};
