import { Router } from 'express';
import { v4 as uuidv4 } from 'uuid';
import prisma from '../db.js';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';

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

router.post('/', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const created = await prisma.card.create({
    data: {
      accountId,
      uid: req.body.uid,
      atqa: req.body.atqa,
      sak: req.body.sak,
      ats: req.body.ats,
      historicalBytes: req.body.historicalBytes,
      label: req.body.label,
      type: req.body.type || 'UNKNOWN',
      aidList: req.body.aidList ? JSON.stringify(req.body.aidList) : null
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

  const updated = await prisma.card.update({
    where: { id: card.id },
    data: {
      uid: req.body.uid ?? card.uid,
      atqa: req.body.atqa ?? card.atqa,
      sak: req.body.sak ?? card.sak,
      ats: req.body.ats ?? card.ats,
      historicalBytes: req.body.historicalBytes ?? card.historicalBytes,
      label: req.body.label ?? card.label,
      type: req.body.type ?? card.type,
      aidList: req.body.aidList ? JSON.stringify(req.body.aidList) : card.aidList
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

router.post('/:id/relay-token', async (req, res) => {
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
