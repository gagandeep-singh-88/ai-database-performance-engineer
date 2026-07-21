import client from './client';
import type {
  AuditLogItem,
  PayloadPreviewResponse,
  PreviewPayload,
  PrivacySettings,
} from '../types/privacy';

export const privacyApi = {
  preview: (payload: PreviewPayload) =>
    client.post<PayloadPreviewResponse>('/privacy/preview', payload).then((r) => r.data),

  getSettings: () => client.get<PrivacySettings>('/privacy/settings').then((r) => r.data),

  updateSettings: (patch: Partial<Pick<PrivacySettings, 'sqlSanitizationEnabled' | 'aiEnabled'>>) =>
    client.put<PrivacySettings>('/privacy/settings', patch).then((r) => r.data),

  audit: (limit = 50) =>
    client.get<AuditLogItem[]>('/privacy/audit', { params: { limit } }).then((r) => r.data),
};
