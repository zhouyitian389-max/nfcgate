import type { NextFunction, Request, Response } from 'express';

export function requestLogger(req: Request, res: Response, next: NextFunction) {
  const start = Date.now();
  const { method, originalUrl } = req;

  res.on('finish', () => {
    const duration = Date.now() - start;
    const status = res.statusCode;
    if (originalUrl.startsWith('/health')) return;
    console.info(JSON.stringify({
      type: 'HTTP',
      method,
      path: originalUrl,
      status,
      duration,
      ip: req.ip,
      userAgent: req.headers['user-agent']?.slice(0, 200)
    }));
  });

  next();
}
