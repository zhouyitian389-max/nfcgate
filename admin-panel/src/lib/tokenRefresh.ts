import { tokenStorage } from './tokenStorage';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080/api';

let refreshPromise: Promise<string | null> | null = null;

async function callRefreshTokenEndpoint(): Promise<string | null> {
  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) return null;

  const response = await fetch(`${API_BASE}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken })
  });
  if (!response.ok) return null;

  const data = await response.json() as {
    accessToken?: string;
    token?: string;
    refreshToken?: string;
    expiresAt?: number;
  };
  const accessToken = data.accessToken || data.token || '';
  const nextRefreshToken = data.refreshToken || '';
  const expiresAt = data.expiresAt ?? (Date.now() + 15 * 60 * 1000);
  if (!accessToken || !nextRefreshToken) return null;

  tokenStorage.setTokens(accessToken, nextRefreshToken, expiresAt);
  return accessToken;
}

export async function requestTokenRefresh(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = callRefreshTokenEndpoint()
      .catch(() => null)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}
