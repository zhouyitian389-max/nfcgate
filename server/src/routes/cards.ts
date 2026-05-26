import { Router } from 'express';
import { v4 as uuidv4 } from 'uuid';
import prisma from '../db.js';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';
import { createCardRateLimiter, relayTokenRateLimiter } from '../middleware/rateLimit.js';
import { validateCardPayload } from '../utils/validators.js';

const router = Router();
router.use(authMiddleware);

router.get('/', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const cards = await prisma.card.findMany({
    where: { accountId },
    orderBy: { createdAt: 'desc' }
  });
  return res.json({ data: cards });
});

router.post('/', createCardRateLimiter, async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const parsed = validateCardPayload(req.body ?? {});
  if (!parsed.ok || !parsed.value) {
    return res.status(400).json({ message: 'Invalid card payload', errors: parsed.errors });
  }

  const created = await prisma.card.create({
    data: {
      accountId,
      uid: parsed.value.uid!,
      atr: parsed.value.atr,
      atqa: parsed.value.atqa,
      sak: parsed.value.sak,
      ats: parsed.value.ats,
      historicalBytes: parsed.value.historicalBytes,
      label: parsed.value.label,
      type: parsed.value.type || 'UNKNOWN',
      aidList: parsed.value.aidList ? JSON.stringify(parsed.value.aidList) : null
    }
  });
  return res.status(201).json(created);
});

router.get('/:id', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const card = await prisma.card.findFirst({ where: { id: req.params.id, accountId } });
  if (!card) return res.status(404).json({ message: 'Card not found' });
  return res.json(card);
});

router.put('/:id', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const card = await prisma.card.findFirst({ where: { id: req.params.id, accountId } });
  if (!card) return res.status(404).json({ message: 'Card not found' });

  const parsed = validateCardPayload(req.body ?? {}, true);
  if (!parsed.ok || !parsed.value) {
    return res.status(400).json({ message: 'Invalid card payload', errors: parsed.errors });
  }

  const updated = await prisma.card.update({
    where: { id: card.id },
    data: {
      uid: parsed.value.uid ?? card.uid,
      atr: parsed.value.atr ?? card.atr,
      atqa: parsed.value.atqa ?? card.atqa,
      sak: parsed.value.sak ?? card.sak,
      ats: parsed.value.ats ?? card.ats,
      historicalBytes: parsed.value.historicalBytes ?? card.historicalBytes,
      label: parsed.value.label ?? card.label,
      type: parsed.value.type ?? card.type,
      aidList: parsed.value.aidList ? JSON.stringify(parsed.value.aidList) : card.aidList
    }
  });

  return res.json(updated);
});

router.delete('/:id', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const card = await prisma.card.findFirst({ where: { id: req.params.id, accountId } });
  if (!card) return res.status(404).json({ message: 'Card not found' });

  await prisma.card.delete({ where: { id: card.id } });
  return res.status(204).send();
});

router.post('/:id/relay-token', relayTokenRateLimiter, async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const card = await prisma.card.findFirst({ where: { id: req.params.id, accountId } });
  if (!card) return res.status(404).json({ message: 'Card not found' });

  const token = uuidv4();
  const ttlMinutes = Number(process.env.RELAY_TOKEN_TTL_MINUTES || 30);
  const expiresAt = new Date(Date.now() + ttlMinutes * 60_000);

  await prisma.card.update({
    where: { id: card.id },
    data: { relayToken: token, relayTokenExpiresAt: expiresAt }
  });

  return res.json({ token, expiresAt });
});

export default router;
