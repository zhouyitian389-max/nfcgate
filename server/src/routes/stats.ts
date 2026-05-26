import { Router } from 'express';
import prisma from '../db.js';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';
import { getActiveSessions } from '../services/relay.js';

const router = Router();
router.use(authMiddleware);

router.get('/', async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;

  const [cards, devices, apduSessions, apduAgg] = await Promise.all([
    prisma.card.count({ where: { accountId: user.accountId } }),
    prisma.device.count({ where: { accountId: user.accountId } }),
    prisma.apduLog.count({ where: { accountId: user.accountId } }),
    prisma.apduLog.aggregate({ where: { accountId: user.accountId }, _sum: { apduCount: true } })
  ]);

  const activeSessions = getActiveSessions(user.accountId).length;

  res.json({
    cards,
    devices,
    sessions: apduSessions,
    activeSessions,
    apduCount: apduAgg._sum.apduCount || 0
  });
});

export default router;
