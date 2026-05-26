import { tokenStorage } from './tokenStorage';

export { tokenStorage };

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080/api';

/** @deprecated Use tokenStorage.getAccessToken() */
export function setAuthToken(token: string): void {
  const currentExpiry = tokenStorage.getAccessTokenExpiry() ?? (Date.now() + 15 * 60 * 1000);
  tokenStorage.setTokens(token, tokenStorage.getRefreshToken() ?? '', currentExpiry);
}

/** @deprecated Use tokenStorage.setTokens() */
export function setRefreshToken(token: string): void {
  const access  = tokenStorage.getAccessToken()       ?? '';
  const expiry  = tokenStorage.getAccessTokenExpiry() ?? (Date.now() + 15 * 60 * 1000);
  tokenStorage.setTokens(access, token, expiry);
}

/** @deprecated Use tokenStorage.clearTokens() */
export function clearAuthToken(): void {
  tokenStorage.clearTokens();
}

let _refreshing: Promise<string | null> | null = null;

async function tryRefreshToken(): Promise<string | null> {
  if (_refreshing) return _refreshing;
  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) return null;

  _refreshing = fetch(`${API_BASE}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken })
  })
    .then(async (r) => {
      if (!r.ok) {
        tokenStorage.clearTokens();
        return null;
      }
      const data = await r.json() as {
        accessToken?: string;
        token?: string;
        refreshToken?: string;
        expiresAt?: number;
      };
      const newAccess  = data.accessToken  || data.token   || '';
      const newRefresh = data.refreshToken || '';
      const expiresAt  = data.expiresAt    ?? (Date.now() + 15 * 60 * 1000);
      if (newAccess && newRefresh) {
        tokenStorage.setTokens(newAccess, newRefresh, expiresAt);
      }
      return newAccess || null;
    })
    .catch(() => {
      tokenStorage.clearTokens();
      return null;
    })
    .finally(() => { _refreshing = null; });
  return _refreshing;
}

export async function apiFetch(path: string, init: RequestInit = {}) {
  // Proactively refresh if the access token is expiring within 60 s.
  if (tokenStorage.isAccessTokenExpiringSoon(60_000)) {
    await tryRefreshToken();
  }

  const token = tokenStorage.getAccessToken();
  const makeHeaders = (t: string | null): Record<string, string> => ({
    'Content-Type': 'application/json',
    ...(t ? { Authorization: 'Bearer ' + t } : {}),
    ...(init.headers as Record<string, string> || {})
  });

  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: makeHeaders(token),
    cache: 'no-store'
  });

  if (!res.ok) {
    if (res.status === 401 && typeof window !== 'undefined') {
      const newToken = await tryRefreshToken();
      if (newToken) {
        const retryRes = await fetch(`${API_BASE}${path}`, {
          ...init,
          headers: makeHeaders(newToken),
          cache: 'no-store'
        });
        if (retryRes.ok) return retryRes.json();
      }
      tokenStorage.clearTokens();
    }
    throw new Error(`API request failed: ${res.status}`);
  }
  return res.json();
}
