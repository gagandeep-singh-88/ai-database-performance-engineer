import client from './client';
import type { UserResponse } from '../types/auth';

export const profileApi = {
  get: () => client.get<UserResponse>('/users/me').then((r) => r.data),

  update: (fullName: string, organization: string) =>
    client.put<UserResponse>('/users/me', { fullName, organization }).then((r) => r.data),

  changePassword: (currentPassword: string, newPassword: string) =>
    client.put('/users/me/password', { currentPassword, newPassword }),
};
