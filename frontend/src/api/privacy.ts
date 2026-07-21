import client from './client';
import type {
  PayloadPreviewResponse,
  PreviewPayload,
  PrivacySettingsDto,
  UpdatePrivacySettingsPayload,
} from '../types/privacy';

export const privacyApi = {
  // Reused by the Query Analyzer's inline "Preview sanitization" step.
  preview: (payload: PreviewPayload) =>
    client.post<PayloadPreviewResponse>('/privacy/preview', payload).then((r) => r.data),

  getSettings: () => client.get<PrivacySettingsDto>('/privacy/settings').then((r) => r.data),

  updateSettings: (payload: UpdatePrivacySettingsPayload) =>
    client.put<PrivacySettingsDto>('/privacy/settings', payload).then((r) => r.data),
};
