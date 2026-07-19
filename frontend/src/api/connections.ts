import client from './client';
import type { ConnectionPayload, ConnectionResponse, ConnectionTestResult } from '../types/connections';

export const connectionsApi = {
  list: () => client.get<ConnectionResponse[]>('/connections').then((r) => r.data),

  create: (payload: ConnectionPayload) =>
    client.post<ConnectionResponse>('/connections', payload).then((r) => r.data),

  test: (id: string) => client.post<ConnectionTestResult>(`/connections/${id}/test`).then((r) => r.data),

  testAdhoc: (payload: Omit<ConnectionPayload, 'name'>) =>
    client.post<ConnectionTestResult>('/connections/test', payload).then((r) => r.data),

  remove: (id: string) => client.delete(`/connections/${id}`),
};
