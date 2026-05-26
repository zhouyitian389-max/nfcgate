import { Router } from 'express';
import prisma from '../db.js';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';
import { apiRateLimiter } from '../middleware/rateLimit.js';
import { createSessionToken, getActiveSessions } from '../services/relay.js';
import { asyncHandler, HttpError } from '../utils/http.js';
import { optionalEnum, optionalString, requireObject } from '../utils/validation.js';

const router = Router();
router.use(apiRateLimiter);
router.use(authMiddleware);
const relayModes = ['NFC_RELAY', 'EMV_EXTERNAL'] as const;

router.get('/sessions', asyncHandler(async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const sessions = getActiveSessions(user.role === 'ADMIN' ? undefined : user.accountId);
  return res.json({ data: sessions });
}));

router.post('/sessions', asyncHandler(async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const body = requireObject(req.body);
  const cardId = optionalString(body.cardId, 'cardId', { maxLength: 64 });
  const mode = optionalEnum(body.mode, 'mode', relayModes) || 'NFC_RELAY';

  if (cardId) {
    const card = await prisma.card.findFirst({ where: { id: cardId, accountId: user.accountId }, select: { id: true } });
    if (!card) {
      throw new HttpError(404, 'Card not found');
    }
  }

  const data = await createSessionToken(user.accountId, cardId, mode);
  return res.status(201).json(data);
}));

export default router;
