import { Router } from 'express';
import { authenticateAccessToken, extractBearerToken } from '../middleware/auth.js';
import { sseHub } from '../services/sseHub.js';
import { asyncHandler, HttpError } from '../utils/http.js';

const router = Router();
router.get('/', asyncHandler(async (req, res) => {
  const token = typeof req.query.token === 'string' ? req.query.token : extractBearerToken(req.headers.authorization);
  if (!token) {
    throw new HttpError(401, 'Missing authorization token');
  }

  const user = await authenticateAccessToken(token);

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
}));

export default router;
