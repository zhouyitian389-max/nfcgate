'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { tokenStorage } from '@/lib/tokenStorage';
import { logout as doLogout } from '@/lib/logoutHandler';
import { requestTokenRefresh } from '@/lib/tokenRefresh';

/** How many milliseconds before expiry we proactively refresh (1 minute). */
const REFRESH_THRESHOLD_MS = 60_000;

/** How often we poll to check token health (10 seconds). */
const POLL_INTERVAL_MS = 10_000;

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
        const accessToken = await requestTokenRefresh();
        if (!accessToken) {
          await doLogout('session_expired');
          return null;
        }
        setExpiresAt(tokenStorage.getAccessTokenExpiry());
        return accessToken;
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
