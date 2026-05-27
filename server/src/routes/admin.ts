import { randomBytes } from 'crypto';
import bcrypt from 'bcrypt';
import { Router } from 'express';
import prisma from '../db.js';
import { type AuthenticatedRequest } from '../middleware/auth.js';
import { revokeUserSessions } from '../services/authSessions.js';
import { normalizeEmail, isStrongPassword } from '../utils/validators.js';

const router = Router();

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

function trimValue(value: unknown, maxLength: number) {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  return trimmed.slice(0, maxLength);
}

function buildPassword(length = 16) {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*';
  const bytes = randomBytes(length);
  let out = '';
  for (const byte of bytes) {
    out += alphabet[byte % alphabet.length];
  }
  return `${out}A1!`;
}

router.get('/users', async (req, res) => {
  const limit = parseLimit(req.query.limit, 50, 200);
  const page = parsePage(req.query.page);
  const skip = (page - 1) * limit;
  const where = req.query.accountId && typeof req.query.accountId === 'string'
    ? { accountId: req.query.accountId }
    : undefined;

  const [users, total] = await Promise.all([
    prisma.user.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      skip,
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
        accountId: true,
        account: { select: { id: true, name: true } }
      }
    }),
    prisma.user.count({ where })
  ]);

  res.json({
    data: users.map((user) => ({
      ...user,
      status: user.lockedUntil && user.lockedUntil.getTime() > Date.now() ? 'locked' : 'active'
    })),
    total,
    page,
    limit
  });
});

router.post('/users', async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const email = normalizeEmail(req.body?.email);
  const password = trimValue(req.body?.password, 256);
  const name = trimValue(req.body?.name, 120);
  const role = trimValue(req.body?.role, 16)?.toUpperCase() === 'ADMIN' ? 'ADMIN' : 'USER';

  if (!email) {
    return res.status(400).json({ message: 'A valid email address is required' });
  }
  if (!isStrongPassword(password)) {
    return res.status(400).json({ message: 'Password must be 8-256 chars and include an uppercase letter, number, and special character' });
  }

  const existing = await prisma.user.findUnique({ where: { email } });
  if (existing) {
    return res.status(409).json({ message: 'Email already registered' });
  }

  const created = await prisma.user.create({
    data: {
      email,
      password: await bcrypt.hash(password, 12),
      name,
      role,
      accountId: user.accountId,
      passwordChangedAt: new Date(),
      mustChangePassword: false
    },
    select: {
      id: true,
      email: true,
      name: true,
      role: true,
      createdAt: true,
      account: { select: { id: true, name: true } }
    }
  });

  return res.status(201).json(created);
});

router.delete('/users/:id', async (req, res) => {
  const admin = (req as AuthenticatedRequest).user!;
  if (admin.id === req.params.id) {
    return res.status(400).json({ message: 'You cannot delete your own admin account' });
  }

  const target = await prisma.user.findUnique({ where: { id: req.params.id }, select: { id: true } });
  if (!target) {
    return res.status(404).json({ message: 'User not found' });
  }

  await revokeUserSessions(target.id, 'Deleted by admin');
  await prisma.user.delete({ where: { id: target.id } });
  return res.status(204).send();
});

router.put('/users/:id/reset-password', async (req, res) => {
  const nextPassword = trimValue(req.body?.password, 256) || buildPassword();
  if (!isStrongPassword(nextPassword)) {
    return res.status(400).json({ message: 'Password must be 8-256 chars and include an uppercase letter, number, and special character' });
  }

  const target = await prisma.user.findUnique({
    where: { id: req.params.id },
    select: {
      id: true,
      email: true,
      name: true,
      role: true,
      account: { select: { id: true, name: true } }
    }
  });
  if (!target) {
    return res.status(404).json({ message: 'User not found' });
  }

  const passwordChangedAt = new Date();
  await prisma.user.update({
    where: { id: target.id },
    data: {
      password: await bcrypt.hash(nextPassword, 12),
      passwordChangedAt,
      mustChangePassword: true,
      loginAttempts: 0,
      lockedUntil: null
    }
  });
  await revokeUserSessions(target.id, 'Password reset by admin');

  res.json({
    message: 'Password reset',
    password: nextPassword,
    mustChangePassword: true,
    passwordChangedAt,
    user: target
  });
});

router.put('/users/:id/toggle-lock', async (req, res) => {
  const target = await prisma.user.findUnique({
    where: { id: req.params.id },
    select: { id: true, lockedUntil: true }
  });
  if (!target) {
    return res.status(404).json({ message: 'User not found' });
  }

  const isLocked = Boolean(target.lockedUntil && target.lockedUntil.getTime() > Date.now());
  const lockedUntil = isLocked ? null : new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
  await prisma.user.update({
    where: { id: target.id },
    data: {
      lockedUntil,
      loginAttempts: isLocked ? 0 : 5
    }
  });
  if (!isLocked) {
    await revokeUserSessions(target.id, 'Locked by admin');
  }

  res.json({
    locked: !isLocked,
    lockedUntil
  });
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
  const limit = parseLimit(req.query.limit, 50, 200);
  const page = parsePage(req.query.page);
  const skip = (page - 1) * limit;
  const where = req.query.accountId && typeof req.query.accountId === 'string'
    ? { accountId: req.query.accountId }
    : undefined;
  const [cards, total] = await Promise.all([
    prisma.card.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      skip,
      take: limit,
      include: { account: { select: { id: true, name: true } } }
    }),
    prisma.card.count({ where })
  ]);
  res.json({ data: cards, total, page, limit });
});

router.delete('/cards/:id', async (req, res) => {
  const card = await prisma.card.findUnique({ where: { id: req.params.id }, select: { id: true } });
  if (card) {
    await prisma.card.delete({ where: { id: card.id } });
    return res.status(204).send();
  }

  const cloudCard = await prisma.cloudCard.findUnique({ where: { id: req.params.id }, select: { id: true, deletedAt: true } });
  if (!cloudCard || cloudCard.deletedAt) {
    return res.status(404).json({ message: 'Card not found' });
  }

  await prisma.cloudCard.update({
    where: { id: cloudCard.id },
    data: { deletedAt: new Date() }
  });
  return res.status(204).send();
});

router.get('/devices', async (req, res) => {
  const limit = parseLimit(req.query.limit, 50, 200);
  const page = parsePage(req.query.page);
  const skip = (page - 1) * limit;
  const where = req.query.accountId && typeof req.query.accountId === 'string'
    ? { accountId: req.query.accountId }
    : undefined;
  const [devices, total] = await Promise.all([
    prisma.device.findMany({
      where,
      orderBy: { updatedAt: 'desc' },
      skip,
      take: limit,
      include: { account: { select: { id: true, name: true } } }
    }),
    prisma.device.count({ where })
  ]);
  res.json({ data: devices, total, page, limit });
});

router.get('/logs', async (req, res) => {
  const limit = parseLimit(req.query.limit, 50, 200);
  const page = parsePage(req.query.page);
  const skip = (page - 1) * limit;
  const {
    sessionId,
    accountId,
    cardId,
    mode,
    from,
    to
  } = req.query as {
    sessionId?: string;
    accountId?: string;
    cardId?: string;
    mode?: string;
    from?: string;
    to?: string;
  };
  const createdAt: { gte?: Date; lte?: Date } = {};
  if (from) createdAt.gte = new Date(from);
  if (to) createdAt.lte = new Date(to);
  const where = {
    ...(sessionId ? { sessionId } : {}),
    ...(accountId ? { accountId } : {}),
    ...(cardId ? { cardId } : {}),
    ...(mode ? { mode } : {}),
    ...(from || to ? { createdAt } : {})
  };
  const [logs, total] = await Promise.all([
    prisma.apduLog.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      skip,
      take: limit,
      include: { account: { select: { id: true, name: true } } }
    }),
    prisma.apduLog.count({ where })
  ]);
  res.json({ data: logs, total, page, limit });
});

router.delete('/logs/:id', async (req, res) => {
  const log = await prisma.apduLog.findUnique({ where: { id: req.params.id }, select: { id: true } });
  if (!log) {
    return res.status(404).json({ message: 'Log not found' });
  }
  await prisma.apduLog.delete({ where: { id: log.id } });
  return res.status(204).send();
});

export default router;
