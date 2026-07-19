import client from './client';
import type { AuthResponse, UserResponse } from '../types/auth';

export const authApi = {
  register: (fullName: string, email: string, password: string) =>
    client.post<AuthResponse>('/auth/register', { fullName, email, password }).then((r) => r.data),

  login: (email: string, password: string) =>
    client.post<AuthResponse>('/auth/login', { email, password }).then((r) => r.data),

  me: () => client.get<UserResponse>('/auth/me').then((r) => r.data),
};
