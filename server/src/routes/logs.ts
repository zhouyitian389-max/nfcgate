import { Router } from 'express';
import prisma from '../db.js';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';

const router = Router();
router.use(authMiddleware);

router.get('/', async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const logs = await prisma.apduLog.findMany({
    where: user.role === 'ADMIN' ? undefined : { accountId: user.accountId },
    orderBy: { createdAt: 'desc' },
    take: 200
  });
  res.json({ data: logs });
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
