import client from './client';
import type { PayloadPreviewResponse, PreviewPayload } from '../types/privacy';

export const privacyApi = {
  // Reused by the Query Analyzer's inline "Preview sanitization" step.
  preview: (payload: PreviewPayload) =>
    client.post<PayloadPreviewResponse>('/privacy/preview', payload).then((r) => r.data),
};
