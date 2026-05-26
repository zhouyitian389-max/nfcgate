import type jwt from 'jsonwebtoken';

const isProduction = process.env.NODE_ENV === 'production';

function parsePositiveInt(value: string | undefined, fallback: number): number {
  const parsed = Number.parseInt(value || '', 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

const configuredJwtSecret = process.env.JWT_SECRET?.trim();

if (!configuredJwtSecret) {
  if (isProduction) {
    throw new Error('JWT_SECRET must be set in production');
  }
  console.warn('JWT_SECRET is not configured; using development fallback secret');
}

export const JWT_SECRET = configuredJwtSecret || 'dev-secret-change-me';
export const JWT_EXPIRES_IN = (process.env.JWT_EXPIRES_IN || '7d') as jwt.SignOptions['expiresIn'];
export const BCRYPT_ROUNDS = Math.min(parsePositiveInt(process.env.BCRYPT_ROUNDS, 12), 14);
export const RELAY_TOKEN_TTL_MINUTES = parsePositiveInt(process.env.RELAY_TOKEN_TTL_MINUTES, 24 * 60);
export const SESSION_TOKEN_TTL_MINUTES = parsePositiveInt(process.env.SESSION_TOKEN_TTL_MINUTES, 60);
export const API_RATE_LIMIT_WINDOW_MS = parsePositiveInt(process.env.API_RATE_LIMIT_WINDOW_MS, 60_000);
export const API_RATE_LIMIT_MAX = parsePositiveInt(process.env.API_RATE_LIMIT_MAX, 300);
export const AUTH_RATE_LIMIT_WINDOW_MS = parsePositiveInt(process.env.AUTH_RATE_LIMIT_WINDOW_MS, 15 * 60_000);
export const AUTH_RATE_LIMIT_MAX = parsePositiveInt(process.env.AUTH_RATE_LIMIT_MAX, 20);

export function getCorsOrigins(): string[] | true {
  const configuredOrigins = process.env.CORS_ORIGIN?.trim();
  if (!configuredOrigins) {
    return ['http://localhost:3000', 'http://127.0.0.1:3000'];
  }

  if (configuredOrigins === '*') {
    return true;
  }

  return configuredOrigins
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean);
}
