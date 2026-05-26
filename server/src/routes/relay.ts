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
  try {
    const data = await createSessionToken(user.accountId, cardId, mode || 'NFC_RELAY');
    return res.status(201).json(data);
  } catch (error) {
    if (error instanceof Error && error.message === 'card_not_found') {
      return res.status(404).json({ message: 'Card not found' });
    }
    throw error;
  }
});

export default router;
