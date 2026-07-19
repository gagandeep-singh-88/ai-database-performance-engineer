import { AxiosError } from 'axios';
import type { ApiError } from '../types/auth';

export function extractApiError(err: unknown): string {
  if (err instanceof AxiosError) {
    const data = err.response?.data as ApiError | undefined;
    if (data?.fieldErrors?.length) {
      return data.fieldErrors.map((fe) => fe.message).join('. ');
    }
    if (data?.message) {
      return data.message;
    }
    if (err.code === 'ERR_NETWORK') {
      return 'Cannot reach the server. Is the backend running?';
    }
  }
  return 'Something went wrong. Please try again.';
}
