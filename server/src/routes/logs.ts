import { Router } from 'express';
import prisma from '../db.js';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';
import { apiRateLimiter } from '../middleware/rateLimit.js';
import { asyncHandler, HttpError } from '../utils/http.js';
import { parsePagination } from '../utils/validation.js';

const router = Router();
router.use(apiRateLimiter);
router.use(authMiddleware);

router.get('/', asyncHandler(async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const page = parsePagination(req.query.page, 1, 10_000);
  const pageSize = parsePagination(req.query.pageSize, 50, 200);
  const where = user.role === 'ADMIN' ? undefined : { accountId: user.accountId };
  const [total, logs] = await Promise.all([
    prisma.apduLog.count({ where }),
    prisma.apduLog.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      skip: (page - 1) * pageSize,
      take: pageSize
    })
  ]);
  res.json({ data: logs, pagination: { page, pageSize, total } });
}));

router.get('/:id', asyncHandler(async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const log = await prisma.apduLog.findUnique({ where: { id: req.params.id } });
  if (!log) throw new HttpError(404, 'Log not found');
  if (user.role !== 'ADMIN' && log.accountId !== user.accountId) {
    throw new HttpError(403, 'Forbidden');
  }

  return res.json(log);
}));

export default router;
