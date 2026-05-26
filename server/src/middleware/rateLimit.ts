import type { Request, RequestHandler } from 'express';

interface RateLimitOptions {
  windowMs: number;
  max: number;
  keyGenerator?: (req: Request) => string;
}

interface RateLimitEntry {
  count: number;
  resetAt: number;
}

export function createRateLimiter(options: RateLimitOptions): RequestHandler {
  const entries = new Map<string, RateLimitEntry>();

  setInterval(() => {
    const now = Date.now();
    for (const [key, entry] of entries.entries()) {
      if (entry.resetAt <= now) {
        entries.delete(key);
      }
    }
  }, options.windowMs).unref();

  return (req, res, next) => {
    const key = options.keyGenerator?.(req) || req.ip || 'unknown';
    const now = Date.now();
    const current = entries.get(key);

    if (!current || current.resetAt <= now) {
      entries.set(key, { count: 1, resetAt: now + options.windowMs });
      return next();
    }

    if (current.count >= options.max) {
      res.setHeader('Retry-After', Math.ceil((current.resetAt - now) / 1000));
      return res.status(429).json({ message: 'Too many requests' });
    }

    current.count += 1;
    return next();
  };
}
