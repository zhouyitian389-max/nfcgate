import { Router } from 'express';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';
import { sseHub } from '../services/sseHub.js';

const router = Router();
router.use(authMiddleware);

router.get('/', (req, res) => {
  const user = (req as AuthenticatedRequest).user!;

  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.flushHeaders();

  const clientId = sseHub.addClient(user.accountId, res);
  const keepAlive = setInterval(() => {
    res.write(': keep-alive\n\n');
  }, 25_000);

  res.write(`event: connected\ndata: ${JSON.stringify({ ok: true })}\n\n`);

  req.on('close', () => {
    clearInterval(keepAlive);
    sseHub.removeClient(clientId);
    res.end();
  });
});

export default router;
