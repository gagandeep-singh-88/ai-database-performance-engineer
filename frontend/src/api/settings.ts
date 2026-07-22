import client from './client';
import type { AboutResponse } from '../types/settings';

export const settingsApi = {
  about: () => client.get<AboutResponse>('/settings/about').then((r) => r.data),
};
