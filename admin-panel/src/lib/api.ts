const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080/api';
const TOKEN_COOKIE = 'jwt';
const REFRESH_TOKEN_COOKIE = 'jwt_refresh';

function readCookie(name: string) {
  if (typeof document === 'undefined') return '';
  const prefix = `${name}=`;
  const match = document.cookie.split('; ').find((item) => item.startsWith(prefix));
  return match ? decodeURIComponent(match.slice(prefix.length)) : '';
}

export function setAuthToken(token: string) {
  if (typeof document === 'undefined') return;
  const secure = window.location.protocol === 'https:' ? '; Secure' : '';
  document.cookie = `${TOKEN_COOKIE}=${encodeURIComponent(token)}; Path=/; SameSite=Strict${secure}; Max-Age=${15 * 60}`;
}

export function setRefreshToken(token: string) {
  if (typeof document === 'undefined') return;
  const secure = window.location.protocol === 'https:' ? '; Secure' : '';
  document.cookie = `${REFRESH_TOKEN_COOKIE}=${encodeURIComponent(token)}; Path=/; SameSite=Strict${secure}; Max-Age=${7 * 24 * 60 * 60}`;
}

export function clearAuthToken() {
  if (typeof document === 'undefined') return;
  const secure = window.location.protocol === 'https:' ? '; Secure' : '';
  document.cookie = `${TOKEN_COOKIE}=; Path=/; SameSite=Strict${secure}; Max-Age=0`;
  document.cookie = `${REFRESH_TOKEN_COOKIE}=; Path=/; SameSite=Strict${secure}; Max-Age=0`;
}

let _refreshing: Promise<string | null> | null = null;

async function tryRefreshToken(): Promise<string | null> {
  if (_refreshing) return _refreshing;
  const refreshToken = readCookie(REFRESH_TOKEN_COOKIE);
  if (!refreshToken) return null;
  _refreshing = fetch(`${API_BASE}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken })
  })
    .then(async (r) => {
      if (!r.ok) {
        clearAuthToken();
        return null;
      }
      const data = await r.json() as { accessToken?: string; token?: string; refreshToken?: string };
      const newAccess = data.accessToken || data.token || '';
      const newRefresh = data.refreshToken || '';
      if (newAccess) setAuthToken(newAccess);
      if (newRefresh) setRefreshToken(newRefresh);
      return newAccess || null;
    })
    .catch(() => {
      clearAuthToken();
      return null;
    })
    .finally(() => { _refreshing = null; });
  return _refreshing;
}

export async function apiFetch(path: string, init: RequestInit = {}) {
  const token = readCookie(TOKEN_COOKIE);
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer' + ' ' + token } : {}),
      ...(init.headers || {})
    },
    cache: 'no-store'
  });
  if (!res.ok) {
    if (res.status === 401 && typeof window !== 'undefined') {
      const newToken = await tryRefreshToken();
      if (newToken) {
        const retryRes = await fetch(`${API_BASE}${path}`, {
          ...init,
          headers: {
            'Content-Type': 'application/json',
            Authorization: 'Bearer' + ' ' + newToken,
            ...(init.headers || {})
          },
          cache: 'no-store'
        });
        if (retryRes.ok) return retryRes.json();
      }
      clearAuthToken();
    }
    throw new Error(`API request failed: ${res.status}`);
  }
  return res.json();
}
