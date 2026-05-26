import { Router } from 'express';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';
import { createSessionToken, getActiveSessions } from '../services/relay.js';

const router = Router();
router.use(authMiddleware);

router.get('/sessions', async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const sessions = getActiveSessions(user.role === 'ADMIN' ? undefined : user.accountId);
  return res.json({ data: sessions });
});

router.post('/sessions', async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const { cardId, mode } = req.body as { cardId?: string; mode?: string };
  const data = await createSessionToken(user.accountId, cardId, mode || 'NFC_RELAY');
  return res.status(201).json(data);
});

export default router;
