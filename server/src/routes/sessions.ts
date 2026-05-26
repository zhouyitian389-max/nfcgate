import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';
import { createSessionToken, endSessionById, getActiveSessions } from '../services/relay.js';

const router = Router();
const sessionRateLimiter = rateLimit({
  windowMs: 60_000,
  max: 120,
  standardHeaders: true,
  legacyHeaders: false,
  message: { message: 'Too many requests' }
});
const sessionWriteRateLimiter = rateLimit({
  windowMs: 60_000,
  max: 60,
  standardHeaders: true,
  legacyHeaders: false,
  message: { message: 'Too many requests' }
});
router.use(sessionRateLimiter);
router.use(authMiddleware);

router.get('/', async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const sessions = getActiveSessions(user.role === 'ADMIN' ? undefined : user.accountId);
  return res.json({ data: sessions });
});

router.post('/:sessionId/end', sessionWriteRateLimiter, async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const reason = typeof req.body?.reason === 'string' ? req.body.reason : 'Ended by API';
  const ended = await endSessionById(req.params.sessionId, user.role === 'ADMIN' ? undefined : user.accountId, reason);
  if (!ended) {
    return res.status(404).json({ message: 'Session not found' });
  }
  return res.status(204).send();
});

router.post('/', sessionWriteRateLimiter, async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const { cardId, mode } = req.body as { cardId?: string; mode?: string };
  const data = await createSessionToken(user.accountId, cardId, mode || 'NFC_RELAY');
  return res.status(201).json(data);
});

export default router;
