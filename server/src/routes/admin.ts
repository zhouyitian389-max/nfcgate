import { Router } from 'express';
import prisma from '../db.js';
import { asyncHandler } from '../utils/http.js';
import { parsePagination } from '../utils/validation.js';

const router = Router();

router.get('/users', asyncHandler(async (req, res) => {
  const page = parsePagination(req.query.page, 1, 10_000);
  const pageSize = parsePagination(req.query.pageSize, 50, 200);
  const [total, users] = await Promise.all([
    prisma.user.count(),
    prisma.user.findMany({
      orderBy: { createdAt: 'desc' },
      include: { account: { select: { id: true, name: true } } },
      skip: (page - 1) * pageSize,
      take: pageSize
    })
  ]);
  res.json({ data: users, pagination: { page, pageSize, total } });
}));

router.get('/stats', asyncHandler(async (_req, res) => {
  const [users, accounts, cards, devices, logs] = await Promise.all([
    prisma.user.count(),
    prisma.account.count(),
    prisma.card.count(),
    prisma.device.count(),
    prisma.apduLog.count()
  ]);

  res.json({ users, accounts, cards, devices, logs });
}));

router.get('/cards', asyncHandler(async (req, res) => {
  const page = parsePagination(req.query.page, 1, 10_000);
  const pageSize = parsePagination(req.query.pageSize, 50, 200);
  const [total, cards] = await Promise.all([
    prisma.card.count(),
    prisma.card.findMany({
      orderBy: { createdAt: 'desc' },
      include: { account: { select: { id: true, name: true } } },
      skip: (page - 1) * pageSize,
      take: pageSize
    })
  ]);
  res.json({ data: cards, pagination: { page, pageSize, total } });
}));

router.get('/devices', asyncHandler(async (req, res) => {
  const page = parsePagination(req.query.page, 1, 10_000);
  const pageSize = parsePagination(req.query.pageSize, 50, 200);
  const [total, devices] = await Promise.all([
    prisma.device.count(),
    prisma.device.findMany({
      orderBy: { updatedAt: 'desc' },
      include: { account: { select: { id: true, name: true } } },
      skip: (page - 1) * pageSize,
      take: pageSize
    })
  ]);
  res.json({ data: devices, pagination: { page, pageSize, total } });
}));

router.get('/logs', asyncHandler(async (req, res) => {
  const page = parsePagination(req.query.page, 1, 10_000);
  const pageSize = parsePagination(req.query.pageSize, 50, 200);
  const [total, logs] = await Promise.all([
    prisma.apduLog.count(),
    prisma.apduLog.findMany({
      orderBy: { createdAt: 'desc' },
      include: { account: { select: { id: true, name: true } } },
      skip: (page - 1) * pageSize,
      take: pageSize
    })
  ]);
  res.json({ data: logs, pagination: { page, pageSize, total } });
}));

export default router;
