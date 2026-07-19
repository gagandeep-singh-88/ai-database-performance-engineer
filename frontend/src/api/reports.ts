import client from './client';

export const reportsApi = {
  /** Returns the PDF as a blob plus the server-suggested filename. */
  downloadPdf: async (connectionId: string): Promise<{ blob: Blob; filename: string }> => {
    const response = await client.get(`/reports/${connectionId}/pdf`, { responseType: 'blob' });
    const disposition: string = response.headers['content-disposition'] ?? '';
    const match = disposition.match(/filename="?([^";]+)"?/);
    return {
      blob: response.data as Blob,
      filename: match?.[1] ?? 'dbperf-report.pdf',
    };
  },
};
