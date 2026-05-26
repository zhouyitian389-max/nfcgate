import { Router } from 'express';
import prisma from '../db.js';

const router = Router();

router.get('/users', async (_req, res) => {
  const users = await prisma.user.findMany({
    orderBy: { createdAt: 'desc' },
    include: { account: { select: { id: true, name: true } } }
  });
  res.json({ data: users });
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

router.get('/cards', async (_req, res) => {
  const cards = await prisma.card.findMany({
    orderBy: { createdAt: 'desc' },
    include: { account: { select: { id: true, name: true } } }
  });
  res.json({ data: cards });
});

router.get('/devices', async (_req, res) => {
  const devices = await prisma.device.findMany({
    orderBy: { updatedAt: 'desc' },
    include: { account: { select: { id: true, name: true } } }
  });
  res.json({ data: devices });
});

router.get('/logs', async (_req, res) => {
  const logs = await prisma.apduLog.findMany({
    orderBy: { createdAt: 'desc' },
    include: { account: { select: { id: true, name: true } } }
  });
  res.json({ data: logs });
});

export default router;
