import jwt from 'jsonwebtoken';

export interface SessionTokenUser {
  id: string;
  accountId: string;
  role: string;
  email: string;
}

export interface SessionTokenClaims {
  sub: string;
  accountId: string;
  role: string;
  email: string;
  sid: string;
  pwd: number;
  type: 'access' | 'refresh';
  iat?: number;
  exp?: number;
}

const DEFAULT_JWT_SECRET = 'dev-secret-change-me';
const DEFAULT_JWT_REFRESH_SECRET = 'dev-refresh-secret-change-me';
const MIN_SECRET_LENGTH = 32;

function resolveSecret(envName: 'JWT_SECRET' | 'JWT_REFRESH_SECRET', fallback: string) {
  const value = process.env[envName] || fallback;
  if (process.env.NODE_ENV === 'production') {
    if (!process.env[envName] || value === fallback || value.length < MIN_SECRET_LENGTH) {
      throw new Error(`${envName} must be set to a random secret with at least ${MIN_SECRET_LENGTH} characters in production`);
    }
  }
  return value;
}

const JWT_SECRET = resolveSecret('JWT_SECRET', DEFAULT_JWT_SECRET);
const JWT_REFRESH_SECRET = resolveSecret('JWT_REFRESH_SECRET', DEFAULT_JWT_REFRESH_SECRET);

function buildClaims(user: SessionTokenUser, sessionId: string, passwordChangedAtMs: number, type: 'access' | 'refresh') {
  return {
    accountId: user.accountId,
    role: user.role,
    email: user.email,
    sid: sessionId,
    pwd: passwordChangedAtMs,
    type
  };
}

/** Parse a JWT expiresIn string/number to milliseconds. Returns 0 on parse failure. */
export function parseExpiresInMs(value: string | number): number {
  if (typeof value === 'number') return value * 1000;
  const m = value.match(/^(\d+)(s|m|h|d)?$/);
  if (!m) return 0;
  const n = parseInt(m[1], 10);
  switch (m[2]) {
    case 'm': return n * 60 * 1000;
    case 'h': return n * 60 * 60 * 1000;
    case 'd': return n * 24 * 60 * 60 * 1000;
    default:  return n * 1000; // 's' or plain seconds
  }
}

function resolveExpiresIn(envName: string, fallback: string): string {
  const raw = process.env[envName] || fallback;
  if (parseExpiresInMs(raw) === 0) {
    console.error(`[tokenService] Invalid ${envName} format "${raw}", falling back to ${fallback}`);
    return fallback;
  }
  return raw;
}

export function getAccessTokenExpiresInMs(): number {
  return parseExpiresInMs(resolveExpiresIn('JWT_EXPIRES_IN', '15m'));
}

export function getRefreshTokenExpiresInMs(): number {
  return parseExpiresInMs(resolveExpiresIn('JWT_REFRESH_EXPIRES_IN', '7d'));
}

export function createAccessToken(user: SessionTokenUser, sessionId: string, passwordChangedAtMs: number): string {
  const expiresIn = resolveExpiresIn('JWT_EXPIRES_IN', '15m') as jwt.SignOptions['expiresIn'];
  return jwt.sign(buildClaims(user, sessionId, passwordChangedAtMs, 'access'), JWT_SECRET, {
    expiresIn,
    subject: user.id
  });
}

export function createRefreshToken(user: SessionTokenUser, sessionId: string, passwordChangedAtMs: number): string {
  const expiresIn = resolveExpiresIn('JWT_REFRESH_EXPIRES_IN', '7d') as jwt.SignOptions['expiresIn'];
  return jwt.sign(buildClaims(user, sessionId, passwordChangedAtMs, 'refresh'), JWT_REFRESH_SECRET, {
    expiresIn,
    subject: user.id
  });
}

function verifyToken(token: string, secret: string, type: 'access' | 'refresh'): SessionTokenClaims {
  const decoded = jwt.verify(token, secret) as SessionTokenClaims;
  if (decoded.type !== type || !decoded.sub || !decoded.sid) {
    throw new Error(`Invalid ${type} token`);
  }
  return decoded;
}

export function verifyAccessToken(token: string): SessionTokenClaims {
  return verifyToken(token, JWT_SECRET, 'access');
}

export function verifyRefreshToken(token: string): SessionTokenClaims {
  return verifyToken(token, JWT_REFRESH_SECRET, 'refresh');
}

export function decodeToken(token: string): SessionTokenClaims | null {
  const decoded = jwt.decode(token);
  if (!decoded || typeof decoded !== 'object') return null;
  return decoded as SessionTokenClaims;
}

export function getTokenExpiresInSeconds(token: string): number {
  const decoded = decodeToken(token);
  if (!decoded?.exp) return 0;
  return Math.max(1, decoded.exp - Math.floor(Date.now() / 1000));
}
