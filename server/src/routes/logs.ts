import { FastifyInstance } from 'fastify';
import { prisma } from '../db.js';
import { authenticate } from '../middleware/auth.js';

export async function logsRoutes(app: FastifyInstance) {
  app.addHook('preHandler', authenticate);

  // GET /api/logs
  app.get('/', async (request) => {
    const accountId = (request as any).accountId;
    const logs = await prisma.operationLog.findMany({
      where: { accountId },
      orderBy: { createdAt: 'desc' },
      take: 100,
    });
    return {
      logs: logs.map((l) => ({
        timestamp: l.createdAt.getTime(),
        action: l.action,
        details: l.details,
      })),
    };
  });
}
