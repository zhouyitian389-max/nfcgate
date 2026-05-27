import type { Request } from 'express';
import rateLimit from 'express-rate-limit';
import jwt from 'jsonwebtoken';

function ipKey(req: Request): string {
  return req.ip || req.socket.remoteAddress || 'unknown';
}

/** For login/register: combine IP + email so per-user limits apply even behind NAT. */
function emailKey(req: Request): string {
  const email = (req.body as Record<string, unknown>)?.email;
  const emailPart = typeof email === 'string' ? email.trim().toLowerCase() : '';
  return `${ipKey(req)}|${emailPart}`;
}

/**
 * For authenticated endpoints: combine IP + userId decoded from the JWT.
 * 
 * Note: jwt.decode() without verify is intentional and safe here - it's used only for key generation.
 * Actual token validation happens in auth middleware via verifyAccessToken().
 * This allows us to extract the userId for per-user rate limiting without validating the signature twice.
 */
function userKey(req: Request): string {
  const auth = req.headers.authorization;
  if (auth?.startsWith('Bearer ')) {
    try {
      const decoded = jwt.decode(auth.slice(7));
      if (decoded && typeof decoded === 'object' && 'sub' in decoded) {
        return `${ipKey(req)}|${decoded.sub as string}`;
      }
    } catch (error) {
      // Malformed token - fall through to IP-only rate limiting
      if (process.env.DEBUG_RATE_LIMIT === 'true') {
        console.warn('[rateLimit] JWT decode error:', error instanceof Error ? error.message : 'Unknown error');
      }
    }
  }
  return ipKey(req);
}

export const apiRateLimiter = rateLimit({
  windowMs: 60_000,
  max: 100,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: userKey,
  message: { message: 'Too many requests' }
});

export const loginRateLimiter = rateLimit({
  windowMs: 60_000,
  max: 5,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: emailKey,
  message: { message: 'Too many login attempts' }
});

export const registerRateLimiter = rateLimit({
  windowMs: 60 * 60 * 1000,
  max: 5,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: emailKey,
  message: { message: 'Too many registration attempts' }
});

export const createCardRateLimiter = rateLimit({
  windowMs: 60_000,
  max: 10,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: userKey,
  message: { message: 'Too many card creation requests' }
});

export const relayTokenRateLimiter = rateLimit({
  windowMs: 60_000,
  max: 20,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: userKey,
  message: { message: 'Too many relay token requests' }
});
