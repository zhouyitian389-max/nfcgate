import dotenv from 'dotenv';
import express from 'express';
import cors from 'cors';
import { createServer } from 'http';
import { Prisma } from '@prisma/client';
import prisma from './db.js';
import {
  API_RATE_LIMIT_MAX,
  API_RATE_LIMIT_WINDOW_MS,
  getCorsOrigins
} from './config.js';
import { authMiddleware, adminOnly } from './middleware/auth.js';
import { createRateLimiter } from './middleware/rateLimit.js';
import { initWebSocket } from './services/relay.js';
import { HttpError } from './utils/http.js';

import authRoutes from './routes/auth.js';
import cardRoutes from './routes/cards.js';
import deviceRoutes from './routes/devices.js';
import relayRoutes from './routes/relay.js';
import eventsRoutes from './routes/events.js';
import adminRoutes from './routes/admin.js';
import logsRoutes from './routes/logs.js';
import statsRoutes from './routes/stats.js';

dotenv.config();

const app = express();
const server = createServer(app);
const corsOrigins = getCorsOrigins();

app.use(cors({
  origin(origin, callback) {
    if (!origin || corsOrigins === true || corsOrigins.includes(origin)) {
      callback(null, true);
      return;
    }
    callback(new HttpError(403, 'Origin not allowed by CORS'));
  },
  allowedHeaders: ['Content-Type', 'Authorization'],
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS']
}));
app.use(express.json({ limit: '10mb' }));
app.use('/api', createRateLimiter({ windowMs: API_RATE_LIMIT_WINDOW_MS, max: API_RATE_LIMIT_MAX }));

app.get('/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

app.use('/api/auth', authRoutes);
app.use('/api/cards', cardRoutes);
app.use('/api/devices', deviceRoutes);
app.use('/api/relay', relayRoutes);
app.use('/api/events', eventsRoutes);
app.use('/api/logs', logsRoutes);
app.use('/api/stats', statsRoutes);

app.use('/api/admin', authMiddleware, adminOnly, adminRoutes);

initWebSocket(server);

app.use((error: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  if (error instanceof HttpError) {
    return res.status(error.status).json({ message: error.message, details: error.details });
  }

  if (error instanceof Prisma.PrismaClientKnownRequestError) {
    if (error.code === 'P2002') {
      return res.status(409).json({ message: 'Resource already exists' });
    }
    if (error.code === 'P2025') {
      return res.status(404).json({ message: 'Resource not found' });
    }
  }

  console.error(error);
  return res.status(500).json({ message: 'Internal server error' });
});

const port = Number(process.env.PORT || 8080);
server.listen(port, () => {
  console.log(`NFCGate server running on :${port}`);
});

process.on('SIGTERM', async () => {
  await prisma.$disconnect();
  server.close(() => process.exit(0));
});

process.on('SIGINT', async () => {
  await prisma.$disconnect();
  server.close(() => process.exit(0));
});
