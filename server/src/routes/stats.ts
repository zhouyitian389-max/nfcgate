import { Router } from 'express';
import prisma from '../db.js';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';
import { getActiveSessions } from '../services/relay.js';

const router = Router();
router.use(authMiddleware);

router.get('/', async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;

  const todayStart = new Date();
  todayStart.setHours(0, 0, 0, 0);

  const [cards, cloudCards, devices, apduSessions, apduAgg, totalUsers, todayActive] = await Promise.all([
    prisma.card.count({ where: { accountId: user.accountId } }),
    prisma.cloudCard.count({ where: { accountId: user.accountId, deletedAt: null } }),
    prisma.device.count({ where: { accountId: user.accountId } }),
    prisma.apduLog.count({ where: { accountId: user.accountId } }),
    prisma.apduLog.aggregate({ where: { accountId: user.accountId }, _sum: { apduCount: true } }),
    prisma.user.count({ where: { accountId: user.accountId } }),
    prisma.device.count({ where: { accountId: user.accountId, lastSeen: { gte: todayStart } } })
  ]);

  const activeSessions = getActiveSessions(user.accountId).length;

  res.json({
    cards,
    cloudCards,
    devices,
    sessions: apduSessions,
    activeSessions,
    apduCount: apduAgg._sum.apduCount || 0,
    total_users: totalUsers,
    your_cards: cloudCards,
    today_active: todayActive
  });
});

export default router;
