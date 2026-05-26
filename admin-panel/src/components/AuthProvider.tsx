'use client';

import type { ReactNode } from 'react';
import { AuthContext } from '@/lib/auth-context';
import { useTokenRefresh } from '@/hooks/useTokenRefresh';
import { logout } from '@/lib/logoutHandler';

export default function AuthProvider({ children }: { children: ReactNode }) {
  const { isRefreshing, expiresAt, refresh } = useTokenRefresh();

  return (
    <AuthContext.Provider value={{ isRefreshing, expiresAt, refresh, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
