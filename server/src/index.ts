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

app.get('/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

app.use('/api/auth', authRoutes);
app.use('/api/cards', cardRoutes);
app.use('/api/devices', deviceRoutes);
app.use('/api/relay', relayRoutes);
app.use('/api/sessions', sessionsRoutes);
app.use('/api/events', eventsRoutes);
app.use('/api/logs', logsRoutes);
app.use('/api/stats', statsRoutes);

app.use('/api/admin', authMiddleware, adminOnly, adminRoutes);

app.use((err: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  const status = err.message.includes('CORS') ? 403 : 500;
  if (status >= 500) {
    console.error(err);
  }
  res.status(status).json({ message: status === 403 ? err.message : 'Internal server error' });
});

initWebSocket(server);

const port = Number(process.env.PORT || 8080);
server.listen(port, () => {
  console.log(`NFCGate server running on :${port}`);
});

process.on('SIGTERM', async () => {
  await prisma.$disconnect();
  server.close(() => process.exit(0));
});
