import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi } from '../api/auth';
import { tokenStore } from '../api/client';
import type { UserResponse } from '../types/auth';

interface AuthContextValue {
  user: UserResponse | null;
  initializing: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  updateUser: (user: UserResponse) => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [initializing, setInitializing] = useState(true);

  useEffect(() => {
    // Restore session from a stored token on hard refresh.
    if (!tokenStore.get()) {
      setInitializing(false);
      return;
    }
    authApi
      .me()
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setInitializing(false));
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      initializing,
      login: async (email, password) => {
        const response = await authApi.login(email, password);
        tokenStore.set(response.accessToken);
        setUser(response.user);
      },
      register: async (fullName, email, password) => {
        const response = await authApi.register(fullName, email, password);
        tokenStore.set(response.accessToken);
        setUser(response.user);
      },
      logout: () => {
        tokenStore.clear();
        setUser(null);
      },
      updateUser: (updated) => setUser(updated),
    }),
    [user, initializing],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
