'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { tokenStorage } from '@/lib/tokenStorage';
import { logout as doLogout } from '@/lib/logoutHandler';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080/api';

/** How many milliseconds before expiry we proactively refresh (1 minute). */
const REFRESH_THRESHOLD_MS = 60_000;

/** How often we poll to check token health (10 seconds). */
const POLL_INTERVAL_MS = 10_000;

async function callRefreshEndpoint(): Promise<{
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
} | null> {
  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) return null;

  try {
    const res = await fetch(`${API_BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken })
    });
    if (!res.ok) return null;

    const data = await res.json() as {
      accessToken?: string;
      token?: string;
      refreshToken?: string;
      expiresAt?: number;
    };

    const newAccess  = data.accessToken  || data.token   || '';
    const newRefresh = data.refreshToken || '';
    const expiresAt  = data.expiresAt    ?? (Date.now() + 15 * 60 * 1000);

    if (!newAccess || !newRefresh) return null;
    return { accessToken: newAccess, refreshToken: newRefresh, expiresAt };
  } catch {
    return null;
  }
}

/**
 * React hook that manages the token lifecycle:
 * - Polls every 10 s and proactively refreshes the access token 1 min before expiry.
 * - Forces logout when the refresh token is also expired / refresh fails.
 */
export function useTokenRefresh() {
  const [isRefreshing, setIsRefreshing]   = useState(false);
  const [expiresAt,    setExpiresAt]      = useState<number | null>(() => tokenStorage.getAccessTokenExpiry());
  const inflightRef                       = useRef<Promise<string | null> | null>(null);

  const refresh = useCallback(async (): Promise<string | null> => {
    if (inflightRef.current) return inflightRef.current;

    inflightRef.current = (async () => {
      setIsRefreshing(true);
      try {
        const result = await callRefreshEndpoint();
        if (!result) {
          await doLogout('session_expired');
          return null;
        }
        tokenStorage.setTokens(result.accessToken, result.refreshToken, result.expiresAt);
        setExpiresAt(result.expiresAt);
        return result.accessToken;
      } finally {
        setIsRefreshing(false);
        inflightRef.current = null;
      }
    })();

    return inflightRef.current;
  }, []);

  useEffect(() => {
    const id = setInterval(async () => {
      const expiry = tokenStorage.getAccessTokenExpiry();
      if (expiry === null) return; // Not logged in — nothing to do.

      setExpiresAt(expiry);

      if (Date.now() >= expiry) {
        // Access token already expired — attempt silent refresh.
        await refresh();
      } else if (Date.now() >= expiry - REFRESH_THRESHOLD_MS) {
        // Token will expire within threshold — proactive refresh.
        await refresh();
      }
    }, POLL_INTERVAL_MS);

    return () => clearInterval(id);
  }, [refresh]);

  return { isRefreshing, expiresAt, refresh };
}
