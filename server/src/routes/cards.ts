import { Router } from 'express';
import { v4 as uuidv4 } from 'uuid';
import prisma from '../db.js';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';

const router = Router();
router.use(authMiddleware);

const HEX_RE = /^[0-9A-Fa-f]+$/;

function isOptionalHex(value: unknown, maxLength: number) {
  if (value == null || value === '') return true;
  return typeof value === 'string' && value.length <= maxLength && HEX_RE.test(value);
}

function isOptionalString(value: unknown, maxLength: number) {
  if (value == null) return true;
  return typeof value === 'string' && value.length <= maxLength;
}

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
  const { uid, atr, atqa, sak, ats, historicalBytes, label, type, aidList, data } = req.body as Record<string, unknown>;
  if (!isOptionalHex(uid, 64) || !uid) {
    return res.status(400).json({ message: 'uid must be a non-empty hex string' });
  }
  if (!isOptionalHex(atr, 256) || !isOptionalHex(atqa, 16) || !isOptionalHex(sak, 8) || !isOptionalHex(ats, 256) || !isOptionalHex(historicalBytes, 512)) {
    return res.status(400).json({ message: 'atr/atqa/sak/ats/historicalBytes must be hex strings' });
  }
  if (!isOptionalString(label, 128) || !isOptionalString(type, 32) || !isOptionalString(data, 4096)) {
    return res.status(400).json({ message: 'label/type/data contains invalid value' });
  }
  if (aidList != null && !Array.isArray(aidList)) {
    return res.status(400).json({ message: 'aidList must be an array' });
  }

  const created = await prisma.card.create({
    data: {
      accountId,
      uid: uid as string,
      atr: (atr as string | undefined) || null,
      data: (data as string | undefined) || null,
      atqa: (atqa as string | undefined) || null,
      sak: (sak as string | undefined) || null,
      ats: (ats as string | undefined) || null,
      historicalBytes: (historicalBytes as string | undefined) || null,
      label: (label as string | undefined) || null,
      type: (type as string | undefined) || 'UNKNOWN',
      aidList: aidList ? JSON.stringify(aidList) : null
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

  const { uid, atr, atqa, sak, ats, historicalBytes, label, type, aidList, data } = req.body as Record<string, unknown>;
  if (!isOptionalHex(uid, 64) || !isOptionalHex(atr, 256) || !isOptionalHex(atqa, 16) || !isOptionalHex(sak, 8) || !isOptionalHex(ats, 256) || !isOptionalHex(historicalBytes, 512)) {
    return res.status(400).json({ message: 'uid/atr/atqa/sak/ats/historicalBytes must be hex strings' });
  }
  if (!isOptionalString(label, 128) || !isOptionalString(type, 32) || !isOptionalString(data, 4096)) {
    return res.status(400).json({ message: 'label/type/data contains invalid value' });
  }
  if (aidList != null && !Array.isArray(aidList)) {
    return res.status(400).json({ message: 'aidList must be an array' });
  }

  const updated = await prisma.card.update({
    where: { id: card.id },
    data: {
      uid: (uid as string | undefined) ?? card.uid,
      atr: (atr as string | undefined) ?? card.atr,
      data: (data as string | undefined) ?? card.data,
      atqa: (atqa as string | undefined) ?? card.atqa,
      sak: (sak as string | undefined) ?? card.sak,
      ats: (ats as string | undefined) ?? card.ats,
      historicalBytes: (historicalBytes as string | undefined) ?? card.historicalBytes,
      label: (label as string | undefined) ?? card.label,
      type: (type as string | undefined) ?? card.type,
      aidList: aidList ? JSON.stringify(aidList) : card.aidList
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
