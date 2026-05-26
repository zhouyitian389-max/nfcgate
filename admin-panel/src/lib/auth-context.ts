'use client';

import { createContext, useContext } from 'react';

export interface AuthState {
  /** Whether a token refresh is currently in flight. */
  isRefreshing: boolean;
  /** Absolute ms timestamp when the current access token expires (null = unknown). */
  expiresAt: number | null;
  /** Trigger a manual token refresh. Resolves with the new access token, or null on failure. */
  refresh: () => Promise<string | null>;
  /** Log the user out and redirect to /login. */
  logout: (reason?: string) => Promise<void>;
}

export const AuthContext = createContext<AuthState>({
  isRefreshing: false,
  expiresAt: null,
  refresh: async () => null,
  logout: async () => undefined
});

export function useAuth(): AuthState {
  return useContext(AuthContext);
}
