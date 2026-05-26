import dotenv from 'dotenv';
import express from 'express';
import cors from 'cors';
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

app.set('trust proxy', 1);
app.use(cors({ origin: process.env.CORS_ORIGIN || '*' }));
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

initWebSocket(server);

const port = Number(process.env.PORT || 8080);
server.listen(port, () => {
  console.log(`NFCGate server running on :${port}`);
});

process.on('SIGTERM', async () => {
  await prisma.$disconnect();
  server.close(() => process.exit(0));
});
