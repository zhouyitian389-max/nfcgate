import dotenv from 'dotenv';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import { createServer } from 'http';
import prisma from './db.js';
import { authMiddleware, adminOnly } from './middleware/auth.js';
import { initWebSocket } from './ws/relay.js';
import healthRoutes from './middleware/health.js';
import { apiRateLimiter } from './middleware/rateLimit.js';
import { requestLogger } from './middleware/requestLogger.js';
import { startDeviceMonitor } from './services/deviceMonitor.js';

import authRoutes from './routes/auth.js';
import cardRoutes from './routes/cards.js';
import deviceRoutes from './routes/devices.js';
import relayRoutes from './routes/relay.js';
import sessionsRoutes from './routes/sessions.js';
import eventsRoutes from './routes/events.js';
import adminRoutes from './routes/admin.js';
import logsRoutes from './routes/logs.js';
import statsRoutes from './routes/stats.js';

dotenv.config();

const app = express();
const server = createServer(app);
const allowedOrigins = (process.env.CORS_ORIGIN || '')
  .split(',')
  .map(o => o.trim())
  .filter(Boolean);
const trustProxy = process.env.TRUST_PROXY_HOPS ? Number(process.env.TRUST_PROXY_HOPS) : 1;

if (process.env.NODE_ENV === 'production' && allowedOrigins.length === 0) {
  console.warn('[security] CORS_ORIGIN is empty in production; cross-origin browser access will be rejected');
}
if (process.env.NODE_ENV === 'production' && allowedOrigins.includes('*')) {
  console.warn('[security] CORS_ORIGIN contains wildcard "*" in production; all cross-origin requests will be allowed');
}

app.set('trust proxy', Number.isFinite(trustProxy) ? trustProxy : 1);
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'"],
      styleSrc: ["'self'", "'unsafe-inline'"],
      imgSrc: ["'self'", 'data:'],
      connectSrc: ["'self'"],
      fontSrc: ["'self'"],
      objectSrc: ["'none'"],
      frameSrc: ["'none'"],
    },
  },
  hsts: process.env.NODE_ENV === 'production' ? { maxAge: 31536000, includeSubDomains: true } : false,
  crossOriginEmbedderPolicy: false,
}));
app.use(cors({
  origin: (origin, callback) => {
    if (!origin) return callback(null, true);
    if (allowedOrigins.length === 0) {
      if (process.env.NODE_ENV !== 'production') return callback(null, true);
      return callback(new Error('CORS not configured'), false);
    }
    if (allowedOrigins.includes('*')) {
      return callback(null, true);
    }
    if (allowedOrigins.includes(origin)) return callback(null, true);
    return callback(new Error(`CORS blocked: ${origin}`), false);
  },
  credentials: true,
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'X-Device-Id'],
}));
app.use(express.json({ limit: '10mb' }));
app.use(requestLogger);
app.use((req, res, next) => {
  if (process.env.NODE_ENV === 'production') {
    const proto = req.headers['x-forwarded-proto'];
    if (!req.secure && proto !== 'https') {
      const host = req.get('host');
      if (host) {
        return res.redirect(301, `https://${host}${req.originalUrl}`);
      }
    }
    res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
  }
  return next();
});
app.use('/health', healthRoutes);
app.use('/api', apiRateLimiter);

app.use('/api/auth', authRoutes);
app.use('/api/cards', cardRoutes);
app.use('/api/devices', deviceRoutes);
app.use('/api/relay', relayRoutes);
app.use('/api/sessions', sessionsRoutes);
app.use('/api/events', eventsRoutes);
app.use('/api/logs', logsRoutes);
app.use('/api/stats', statsRoutes);

app.use('/api/admin', authMiddleware, adminOnly, adminRoutes);

app.use('/api/v1/auth', authRoutes);
app.use('/api/v1/cards', cardRoutes);
app.use('/api/v1/devices', deviceRoutes);
app.use('/api/v1/relay', relayRoutes);
app.use('/api/v1/sessions', sessionsRoutes);
app.use('/api/v1/events', eventsRoutes);
app.use('/api/v1/logs', logsRoutes);
app.use('/api/v1/stats', statsRoutes);
app.use('/api/v1/admin', authMiddleware, adminOnly, adminRoutes);

app.use((err: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  const status = err.message.includes('CORS') ? 403 : 500;
  if (status >= 500) {
    console.error(err);
  }
  res.status(status).json({ message: status === 403 ? err.message : 'Internal server error' });
});

const shutdownWebSocket = initWebSocket(server);
let stopDeviceMonitor: () => void = () => undefined;

const port = Number(process.env.PORT || 8080);
server.listen(port, () => {
  stopDeviceMonitor = startDeviceMonitor();
  console.log(`NFCGate server running on :${port}`);
});

process.on('SIGTERM', async () => {
  stopDeviceMonitor();
  server.close(async () => {
    await shutdownWebSocket();
    await prisma.$disconnect();
    process.exit(0);
  });
  setTimeout(() => process.exit(1), 5000).unref();
});
