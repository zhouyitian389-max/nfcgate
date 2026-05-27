import { Router } from 'express';
import prisma from '../db.js';
import { createManagedUser, deleteManagedUser, resetManagedUserPassword, setManagedUserLock } from '../services/adminUsers.js';
import { closeSessionsByAccountId } from '../services/relay.js';

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

function parseDate(raw: unknown) {
  if (typeof raw !== 'string' || !raw.trim()) return undefined;
  const value = new Date(raw);
  return Number.isNaN(value.getTime()) ? undefined : value;
}

router.get('/users', async (req, res) => {
  const limit = parseLimit(req.query.limit, 50, 200);
  const page = parsePage(req.query.page);
  const skip = (page - 1) * limit;
  const search = typeof req.query.search === 'string' ? req.query.search.trim() : '';
  const where = search
    ? {
        OR: [
          { email: { contains: search, mode: 'insensitive' as const } },
          { name: { contains: search, mode: 'insensitive' as const } }
        ]
      }
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
        account: { select: { id: true, name: true } }
      }
    }),
    prisma.user.count({ where })
  ]);

  res.json({ data: users, limit, page, total });
});

router.post('/users', async (req, res) => {
  try {
    const user = await createManagedUser(req.body as Record<string, unknown>);
    return res.status(201).json({ user });
  } catch (error) {
    if (error instanceof Error) {
      if (error.message === 'Email already registered') {
        return res.status(409).json({ message: error.message });
      }
      return res.status(400).json({ message: error.message });
    }
    throw error;
  }
});

router.delete('/users/:id', async (req, res) => {
  try {
    const deleted = await deleteManagedUser(req.params.id);
    await closeSessionsByAccountId(deleted.accountId, 'Account removed by admin');
    return res.status(204).send();
  } catch (error) {
    if (error instanceof Error && error.message === 'User not found') {
      return res.status(404).json({ message: error.message });
    }
    throw error;
  }
});

router.put('/users/:id/reset-password', async (req, res) => {
  try {
    const password = (req.body as { password?: string } | undefined)?.password;
    const result = await resetManagedUserPassword(req.params.id, password);
    await closeSessionsByAccountId(result.user.accountId, 'Password reset by admin');
    return res.json({
      message: 'Password reset',
      userId: result.user.id,
      passwordChangedAt: result.passwordChangedAt
    });
  } catch (error) {
    if (error instanceof Error) {
      if (error.message === 'User not found') {
        return res.status(404).json({ message: error.message });
      }
      return res.status(400).json({ message: error.message });
    }
    throw error;
  }
});

router.put('/users/:id/toggle-lock', async (req, res) => {
  try {
    const locked = Boolean((req.body as { locked?: boolean } | undefined)?.locked);
    const result = await setManagedUserLock(req.params.id, locked);
    if (locked) {
      await closeSessionsByAccountId(result.user.accountId, 'Account locked by admin');
    }
    return res.json({
      message: locked ? 'User locked' : 'User unlocked',
      userId: result.user.id,
      lockedUntil: result.lockedUntil
    });
  } catch (error) {
    if (error instanceof Error) {
      if (error.message === 'User not found') {
        return res.status(404).json({ message: error.message });
      }
      return res.status(400).json({ message: error.message });
    }
    throw error;
  }
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
  const [cards, total] = await Promise.all([
    prisma.card.findMany({
      orderBy: { createdAt: 'desc' },
      skip,
      take: limit,
      include: { account: { select: { id: true, name: true } } }
    }),
    prisma.card.count()
  ]);

  res.json({ data: cards, limit, page, total });
});

router.delete('/cards/:id', async (req, res) => {
  const existing = await prisma.card.findUnique({ where: { id: req.params.id }, select: { id: true } });
  if (!existing) {
    return res.status(404).json({ message: 'Card not found' });
  }
  await prisma.card.delete({ where: { id: req.params.id } });
  return res.status(204).send();
});

router.get('/devices', async (req, res) => {
  const limit = parseLimit(req.query.limit, 50, 200);
  const page = parsePage(req.query.page);
  const skip = (page - 1) * limit;
  const [devices, total] = await Promise.all([
    prisma.device.findMany({
      orderBy: { updatedAt: 'desc' },
      skip,
      take: limit,
      include: { account: { select: { id: true, name: true } } }
    }),
    prisma.device.count()
  ]);

  res.json({ data: devices, limit, page, total });
});

router.get('/logs', async (req, res) => {
  const limit = parseLimit(req.query.limit, 50, 200);
  const page = parsePage(req.query.page);
  const skip = (page - 1) * limit;
  const from = parseDate(req.query.from);
  const to = parseDate(req.query.to);
  const where = {
    ...(typeof req.query.sessionId === 'string' && req.query.sessionId.trim() ? { sessionId: req.query.sessionId.trim() } : {}),
    ...(typeof req.query.mode === 'string' && req.query.mode.trim() ? { mode: req.query.mode.trim() } : {}),
    ...(typeof req.query.accountId === 'string' && req.query.accountId.trim() ? { accountId: req.query.accountId.trim() } : {}),
    ...(from || to
      ? {
          createdAt: {
            ...(from ? { gte: from } : {}),
            ...(to ? { lte: to } : {})
          }
        }
      : {})
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

  res.json({ data: logs, limit, page, total });
});

export default router;
