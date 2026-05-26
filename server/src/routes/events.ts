import { Router, type Response } from 'express';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';
import { sseHub } from '../services/sseHub.js';

const router = Router();
router.use(authMiddleware);

function streamEvents(req: AuthenticatedRequest, res: Response) {
  const user = req.user!;

  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache, no-transform');
  res.setHeader('Connection', 'keep-alive');
  res.setHeader('X-Accel-Buffering', 'no');
  res.flushHeaders();

  const clientId = sseHub.addClient(user.accountId, res);
  const keepAlive = setInterval(() => {
    res.write(': keep-alive\n\n');
  }, 25_000);

  res.write(`event: connected\ndata: ${JSON.stringify({ ok: true, accountId: user.accountId, sessionId: user.sessionId })}\n\n`);

  req.on('close', () => {
    clearInterval(keepAlive);
    sseHub.removeClient(clientId);
    res.end();
  });
}

router.get('/', (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  streamEvents({ ...(req as AuthenticatedRequest), user }, res);
});

router.get('/stream', (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  streamEvents({ ...(req as AuthenticatedRequest), user }, res);
});

export default router;
