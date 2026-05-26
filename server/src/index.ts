import express from 'express';
import cors from 'cors';
import { createServer } from 'http';
import prisma from './db.js';
import { authMiddleware, adminOnly } from './middleware/auth.js';
import { initWebSocket } from './services/relay.js';

// Route imports
import authRoutes from './routes/auth.js';
import cardRoutes from './routes/cards.js';
import deviceRoutes from './routes/devices.js';
import relayRoutes from './routes/relay.js';
import eventsRoutes from './routes/events.js';
import adminRoutes from './routes/admin.js';
import logsRoutes from './routes/logs.js';
import statsRoutes from './routes/stats.js';

const app = express();
const server = createServer(app);

// Middleware
app.use(cors({ origin: process.env.CORS_ORIGIN || '*' }));
app.use(express.json({ limit: '10mb' }));

// Health check
app.get('/health', (_, res) => res.json({ status: 'ok', timestamp: new Date().toISOString() }));

// Public routes
app.use('/api/auth', authRoutes);

// Protected routes
app.use('/api/cards', cardRoutes);
app.use('/api/devices', deviceRoutes);
app.use('/api/relay', relayRoutes);
app.use('/api/events', eventsRoutes);
app.use('/api/stats', statsRoutes);

// Admin routes
app.use('/api/admin', authMiddleware, adminOnly, adminRoutes);
app.use('/api/admin/logs', authMiddleware, adminOnly, logsRoutes);

// Initialize WebSocket relay
initWebSocket(server);

// Start server
const PORT = process.env.PORT || 8080;
server.listen(PORT, () => {
  console.log(`🚀 NFCGate Server running on port ${PORT}`);
  console.log(`📡 WebSocket relay available at ws://localhost:${PORT}/ws/relay`);
});

// Graceful shutdown
process.on('SIGTERM', async () => {
  console.log('Shutting down...');
  await prisma.$disconnect();
  server.close();
  process.exit(0);
});

export default app;
