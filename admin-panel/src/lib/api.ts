const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080/api';
const TOKEN_COOKIE = 'jwt';

function readCookie(name: string) {
  if (typeof document === 'undefined') return '';
  const prefix = `${name}=`;
  const match = document.cookie.split('; ').find((item) => item.startsWith(prefix));
  return match ? decodeURIComponent(match.slice(prefix.length)) : '';
}

export function setAuthToken(token: string) {
  if (typeof document === 'undefined') return;
  const secure = window.location.protocol === 'https:' ? '; Secure' : '';
  document.cookie = `${TOKEN_COOKIE}=${encodeURIComponent(token)}; Path=/; SameSite=Strict${secure}; Max-Age=${7 * 24 * 60 * 60}`;
}

export function clearAuthToken() {
  if (typeof document === 'undefined') return;
  const secure = window.location.protocol === 'https:' ? '; Secure' : '';
  document.cookie = `${TOKEN_COOKIE}=; Path=/; SameSite=Strict${secure}; Max-Age=0`;
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
    if (res.status === 401) {
      clearAuthToken();
    }
    throw new Error(`API request failed: ${res.status}`);
  }
  return res.json();
}
