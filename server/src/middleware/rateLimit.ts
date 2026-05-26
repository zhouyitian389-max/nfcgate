import rateLimit from 'express-rate-limit';
import {
  API_RATE_LIMIT_MAX,
  API_RATE_LIMIT_WINDOW_MS,
  AUTH_RATE_LIMIT_MAX,
  AUTH_RATE_LIMIT_WINDOW_MS
} from '../config.js';

export const apiRateLimiter = rateLimit({
  windowMs: API_RATE_LIMIT_WINDOW_MS,
  limit: API_RATE_LIMIT_MAX,
  standardHeaders: true,
  legacyHeaders: false,
  validate: false
});

export const authRateLimiter = rateLimit({
  windowMs: AUTH_RATE_LIMIT_WINDOW_MS,
  limit: AUTH_RATE_LIMIT_MAX,
  standardHeaders: true,
  legacyHeaders: false,
  validate: false,
  keyGenerator: (req) => `${req.ip}:${String(req.body?.email || 'anonymous').toLowerCase()}`
});
