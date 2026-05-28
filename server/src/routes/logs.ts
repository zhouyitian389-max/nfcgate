import { Router } from 'express';
import prisma from '../db.js';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';

const router = Router();
router.use(authMiddleware);

function parseLimit(raw: unknown, defaultValue: number, maxValue: number) {
  const parsed = typeof raw === 'string' ? Number(raw) : Number(raw ?? defaultValue);
  if (!Number.isFinite(parsed) || parsed <= 0) return defaultValue;
  return Math.min(maxValue, Math.floor(parsed));
}

function parsePage(raw: unknown) {
  const parsed = typeof raw === 'string' ? Number(raw) : Number(raw ?? 1);
  if (!Number.isFinite(parsed) || parsed <= 0) return 1;
  return Math.floor(parsed);
}

router.get('/', async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const {
    sessionId,
    cardId,
    mode,
    from,
    to
  } = req.query as { sessionId?: string; cardId?: string; mode?: string; from?: string; to?: string };

  const createdAt: { gte?: Date; lte?: Date } = {};
  if (from) createdAt.gte = new Date(from);
  if (to) createdAt.lte = new Date(to);
  const limit = parseLimit(req.query.limit, 50, 200);
  const page = parsePage(req.query.page);
  const skip = (page - 1) * limit;
  const where = {
    ...(user.role === 'ADMIN' ? {} : { accountId: user.accountId }),
    ...(sessionId ? { sessionId } : {}),
    ...(cardId ? { cardId } : {}),
    ...(mode ? { mode } : {}),
    ...(from || to ? { createdAt } : {})
  };

  const [logs, total] = await Promise.all([
    prisma.apduLog.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      skip,
      take: limit
    }),
    prisma.apduLog.count({ where })
  ]);
  res.json({
    data: logs,
    total,
    page,
    limit,
    logs: logs.map((log) => ({
      id: log.id,
      timestamp: log.createdAt.getTime(),
      action: `${log.mode} session`,
      details: `session=${log.sessionId} apdu=${log.apduCount} duration=${log.duration}ms${log.closedReason ? ` reason=${log.closedReason}` : ''}`
    }))
  });
});

router.get('/:id', async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const log = await prisma.apduLog.findUnique({ where: { id: req.params.id } });
  if (!log) return res.status(404).json({ message: 'Log not found' });
  if (user.role !== 'ADMIN' && log.accountId !== user.accountId) {
    return res.status(403).json({ message: 'Forbidden' });
  }

  return res.json(log);
});

export default router;
