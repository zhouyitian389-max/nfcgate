import type { Request } from 'express';
import rateLimit from 'express-rate-limit';

function keyGenerator(req: Request) {
  return req.ip || req.socket.remoteAddress || 'unknown';
}

export const apiRateLimiter = rateLimit({
  windowMs: 60_000,
  max: 100,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator,
  message: { message: 'Too many requests' }
});

export const loginRateLimiter = rateLimit({
  windowMs: 60_000,
  max: 5,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator,
  message: { message: 'Too many login attempts' }
});

export const createCardRateLimiter = rateLimit({
  windowMs: 60_000,
  max: 10,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator,
  message: { message: 'Too many card creation requests' }
});

export const relayTokenRateLimiter = rateLimit({
  windowMs: 60_000,
  max: 20,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator,
  message: { message: 'Too many relay token requests' }
});
