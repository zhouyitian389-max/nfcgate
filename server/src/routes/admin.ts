import { Router } from 'express';
import prisma from '../db.js';

const router = Router();

function parseLimit(raw: unknown, defaultValue: number, maxValue: number) {
  const parsed = typeof raw === 'string' ? Number(raw) : Number(raw ?? defaultValue);
  if (!Number.isFinite(parsed) || parsed <= 0) return defaultValue;
  return Math.min(maxValue, Math.floor(parsed));
}

router.get('/users', async (req, res) => {
  const limit = parseLimit(req.query.limit, 200, 1000);
  const users = await prisma.user.findMany({
    orderBy: { createdAt: 'desc' },
    take: limit,
    select: {
      id: true,
      email: true,
      name: true,
      role: true,
      createdAt: true,
      updatedAt: true,
      loginAttempts: true,
      lockedUntil: true,
      mustChangePassword: true,
      account: { select: { id: true, name: true } }
    }
  });
  res.json({ data: users, limit });
});

router.get('/stats', async (_req, res) => {
  const [users, accounts, cards, devices, logs] = await Promise.all([
    prisma.user.count(),
    prisma.account.count(),
    prisma.card.count(),
    prisma.device.count(),
    prisma.apduLog.count()
  ]);

  res.json({ users, accounts, cards, devices, logs });
});

router.get('/cards', async (req, res) => {
  const limit = parseLimit(req.query.limit, 200, 1000);
  const cards = await prisma.card.findMany({
    orderBy: { createdAt: 'desc' },
    take: limit,
    include: { account: { select: { id: true, name: true } } }
  });
  res.json({ data: cards, limit });
});

router.get('/devices', async (req, res) => {
  const limit = parseLimit(req.query.limit, 200, 1000);
  const devices = await prisma.device.findMany({
    orderBy: { updatedAt: 'desc' },
    take: limit,
    include: { account: { select: { id: true, name: true } } }
  });
  res.json({ data: devices, limit });
});

router.get('/logs', async (req, res) => {
  const limit = parseLimit(req.query.limit, 200, 1000);
  const logs = await prisma.apduLog.findMany({
    orderBy: { createdAt: 'desc' },
    take: limit,
    include: { account: { select: { id: true, name: true } } }
  });
  res.json({ data: logs, limit });
});

export default router;
