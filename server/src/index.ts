import dotenv from 'dotenv';
import express from 'express';
import cors from 'cors';
import { createServer } from 'http';
import prisma from './db.js';
import { authMiddleware, adminOnly } from './middleware/auth.js';
import { initWebSocket } from './services/relay.js';

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

app.use(cors({ origin: process.env.CORS_ORIGIN || '*' }));
app.use(express.json({ limit: '10mb' }));

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

const port = Number(process.env.PORT || 8080);
server.listen(port, () => {
  console.log(`NFCGate server running on :${port}`);
});

process.on('SIGTERM', async () => {
  await prisma.$disconnect();
  server.close(() => process.exit(0));
});
