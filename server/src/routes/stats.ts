import { FastifyInstance } from 'fastify';
import { prisma } from '../db.js';
import { authenticate } from '../middleware/auth.js';

export async function statsRoutes(app: FastifyInstance) {
  app.addHook('preHandler', authenticate);

  // GET /api/stats
  app.get('/', async () => {
    const [users, cards, devices, sessions, activeSessions, logs] = await Promise.all([
      prisma.account.count(),
      prisma.card.count(),
      prisma.device.count(),
      prisma.relaySession.count(),
      prisma.relaySession.count({ where: { status: 'active' } }),
      prisma.operationLog.count(),
    ]);
    return { users, cards, devices, sessions, activeSessions, logs };
  });
}
