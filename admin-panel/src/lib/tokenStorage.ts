/**
 * TokenStorage — manages access/refresh tokens and expiry timestamps.
 *
 * Tokens are stored as SameSite=Strict cookies (no HttpOnly so the JS
 * layer can read them for proactive refresh).  The expiry timestamp is
 * stored in a separate short-lived cookie so it survives page reloads.
 */

const ACCESS_COOKIE  = 'jwt';
const REFRESH_COOKIE = 'jwt_refresh';
const EXPIRES_COOKIE = 'jwt_expires_at';

function secure(): string {
  if (typeof window === 'undefined') return '';
  return window.location.protocol === 'https:' ? '; Secure' : '';
}

function writeCookie(name: string, value: string, maxAgeSeconds: number): void {
  if (typeof document === 'undefined') return;
  document.cookie = `${name}=${encodeURIComponent(value)}; Path=/; SameSite=Strict${secure()}; Max-Age=${maxAgeSeconds}`;
}

function readCookie(name: string): string {
  if (typeof document === 'undefined') return '';
  const prefix = `${name}=`;
  const match = document.cookie.split('; ').find((c) => c.startsWith(prefix));
  return match ? decodeURIComponent(match.slice(prefix.length)) : '';
}

function deleteCookie(name: string): void {
  if (typeof document === 'undefined') return;
  document.cookie = `${name}=; Path=/; SameSite=Strict${secure()}; Max-Age=0`;
}

export class TokenStorage {
  /**
   * Persist access token, refresh token and absolute expiry timestamp.
   * @param accessToken   JWT access token
   * @param refreshToken  JWT refresh token
   * @param expiresAt     ms since epoch when the access token expires
   */
  setTokens(accessToken: string, refreshToken: string, expiresAt: number): void {
    const accessMaxAge  = Math.max(0, Math.floor((expiresAt - Date.now()) / 1000));
    const refreshMaxAge = 7 * 24 * 60 * 60; // 7 days

    writeCookie(ACCESS_COOKIE,  accessToken,       accessMaxAge || 15 * 60);
    writeCookie(REFRESH_COOKIE, refreshToken,      refreshMaxAge);
    writeCookie(EXPIRES_COOKIE, String(expiresAt), refreshMaxAge);
  }

  getAccessToken(): string | null {
    return readCookie(ACCESS_COOKIE) || null;
  }

  getRefreshToken(): string | null {
    return readCookie(REFRESH_COOKIE) || null;
  }

  getAccessTokenExpiry(): number | null {
    const raw = readCookie(EXPIRES_COOKIE);
    const ts  = raw ? parseInt(raw, 10) : NaN;
    return Number.isFinite(ts) ? ts : null;
  }

  isAccessTokenExpired(): boolean {
    const expiry = this.getAccessTokenExpiry();
    return expiry === null || Date.now() >= expiry;
  }

  /** Returns true when the access token will expire within `thresholdMs` milliseconds. */
  isAccessTokenExpiringSoon(thresholdMs = 60_000): boolean {
    const expiry = this.getAccessTokenExpiry();
    return expiry === null || Date.now() >= expiry - thresholdMs;
  }

  clearTokens(): void {
    deleteCookie(ACCESS_COOKIE);
    deleteCookie(REFRESH_COOKIE);
    deleteCookie(EXPIRES_COOKIE);
  }
}

export const tokenStorage = new TokenStorage();
