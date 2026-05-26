import { tokenStorage } from './tokenStorage';
import { requestTokenRefresh } from './tokenRefresh';

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

export async function apiFetch(path: string, init: RequestInit = {}) {
  // Proactively refresh if the access token is expiring within 60 s.
  if (tokenStorage.isAccessTokenExpiringSoon(60_000)) {
    const refreshed = await requestTokenRefresh();
    if (!refreshed) {
      tokenStorage.clearTokens();
    }
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
      const newToken = await requestTokenRefresh();
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
