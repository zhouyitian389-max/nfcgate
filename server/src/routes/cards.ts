import { Router } from 'express';
import { createHash, createHmac } from 'crypto';
import { v4 as uuidv4 } from 'uuid';
import prisma from '../db.js';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';
import { createCardRateLimiter, relayTokenRateLimiter } from '../middleware/rateLimit.js';
import { decryptString, encryptString } from '../utils/crypto.js';
import { normalizeTrack2, validateCardPayload } from '../utils/validators.js';

const router = Router();
router.use(authMiddleware);

function panHashSecret() {
  return process.env.PAN_HASH_SECRET || process.env.CARD_ENCRYPTION_KEY || 'dev-pan-hash-secret-change-me';
}

function hashPan(pan: string): string {
  return createHmac('sha256', panHashSecret()).update(pan).digest('hex');
}

function hashPanLegacy(pan: string): string {
  return createHash('sha256').update(pan).digest('hex');
}

function isOptionalString(value: unknown, maxLength: number) {
  if (value == null) return true;
  return typeof value === 'string' && value.length <= maxLength;
}

function trimString(value: unknown, maxLength: number) {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  return trimmed.slice(0, maxLength);
}

function maskPan(value: string) {
  const digits = value.replace(/\s+/g, '');
  const last4 = digits.slice(-4);
  return last4 ? `**** **** **** ${last4}` : '****';
}

function decryptOptional(value: string | null) {
  if (!value) return null;
  try {
    return decryptString(value);
  } catch {
    return null;
  }
}

function resolveCardSecret(card: {
  panEncrypted: string | null;
  pan: string | null;
  track2Encrypted: string | null;
  track2: string | null;
}) {
  return {
    pan: card.panEncrypted ? (decryptOptional(card.panEncrypted) ?? card.pan) : card.pan,
    track2: card.track2Encrypted ? (decryptOptional(card.track2Encrypted) ?? card.track2) : card.track2
  };
}

async function upsertCloudCard(accountId: string, item: Record<string, unknown>) {
  const encryptedBlob = trimString(item.blob, 16_384);
  const pan = trimString(item.pan, 32);
  const brand = trimString(item.brand, 32) || 'UNKNOWN';
  const holder = trimString(item.holder, 128);
  const expiry = trimString(item.expiry, 32);
  const track2 = normalizeTrack2(item.track2);
  const note = trimString(item.note, 256);

  if (!encryptedBlob && !pan) {
    throw new Error('Each card must contain either blob or pan');
  }

  const panEncrypted = pan ? encryptString(pan) : null;
  const track2Encrypted = track2 ? encryptString(track2) : null;
  const panHashValue = pan ? hashPan(pan) : null;
  const legacyPanHashValue = pan ? hashPanLegacy(pan) : null;

  return prisma.$transaction(async (tx) => {
    let existing = encryptedBlob
      ? await tx.cloudCard.findFirst({ where: { accountId, encryptedBlob, deletedAt: null } })
      : null;

    if (!existing && panHashValue) {
      existing = await tx.cloudCard.findFirst({
        where: {
          accountId,
          deletedAt: null,
          panHash: { in: legacyPanHashValue ? [panHashValue, legacyPanHashValue] : [panHashValue] }
        }
      }) ?? null;
    }

    if (existing) {
      return tx.cloudCard.update({
        where: { id: existing.id },
        data: {
          encryptedBlob: encryptedBlob ?? existing.encryptedBlob,
          panEncrypted: panEncrypted ?? existing.panEncrypted,
          pan: pan ? maskPan(pan) : existing.pan,
          panHash: panHashValue ?? existing.panHash,
          brand,
          holder: holder ?? existing.holder,
          expiry: expiry ?? existing.expiry,
          track2Encrypted: track2Encrypted ?? existing.track2Encrypted,
          track2: track2 ? null : existing.track2,
          note: note ?? existing.note,
          deletedAt: null
        }
      });
    }

    return tx.cloudCard.create({
      data: {
        accountId,
        encryptedBlob: encryptedBlob ?? null,
        panEncrypted,
        pan: pan ? maskPan(pan) : null,
        panHash: panHashValue,
        brand,
        holder: holder ?? null,
        expiry: expiry ?? null,
        track2Encrypted,
        track2: null,
        note: note ?? null,
        source: encryptedBlob ? 'encrypted-mobile' : 'plain-mobile'
      }
    });
  });
}

function mapCloudCard(card: {
  id: string;
  encryptedBlob: string | null;
  panEncrypted: string | null;
  pan: string | null;
  brand: string | null;
  holder: string | null;
  expiry: string | null;
  track2Encrypted: string | null;
  track2: string | null;
  note: string | null;
  expiresAt: Date | null;
}) {
  const secrets = resolveCardSecret(card);
  return {
    id: card.id,
    card_id: card.id,
    blob: card.encryptedBlob ?? undefined,
    pan: secrets.pan ?? '',
    brand: card.brand ?? 'UNKNOWN',
    holder: card.holder ?? '',
    expiry: card.expiry ?? '',
    track2: secrets.track2 ?? '',
    note: card.note ?? '',
    expired: card.expiresAt ? card.expiresAt.getTime() <= Date.now() : false
  };
}

router.get('/', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const cards = await prisma.card.findMany({
    where: { accountId },
    orderBy: { createdAt: 'desc' }
  });
  return res.json({ data: cards });
});

router.get('/pull', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const cards = await prisma.cloudCard.findMany({
    where: { accountId, deletedAt: null },
    orderBy: { createdAt: 'desc' },
    take: 500
  });
  return res.json({
    cards: cards.map(mapCloudCard)
  });
});

router.post('/upload', createCardRateLimiter, async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const rawCards = Array.isArray(req.body?.cards) ? req.body.cards : [];
  if (rawCards.length === 0) {
    return res.status(400).json({ message: 'cards must be a non-empty array' });
  }

  const ids: string[] = [];
  for (const entry of rawCards) {
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
      return res.status(400).json({ message: 'cards entries must be objects' });
    }
    const card = await upsertCloudCard(accountId, entry as Record<string, unknown>);
    ids.push(card.id);
  }

  return res.status(201).json({
    added: ids.length,
    card_ids: ids
  });
});

router.post('/ack', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const cardIds = Array.isArray(req.body?.card_ids)
    ? req.body.card_ids.filter((value: unknown): value is string => typeof value === 'string' && value.trim().length > 0)
    : [];

  if (cardIds.length === 0) {
    return res.status(400).json({ message: 'card_ids must be a non-empty array' });
  }

  const result = await prisma.cloudCard.updateMany({
    where: {
      accountId,
      id: { in: cardIds },
      deletedAt: null
    },
    data: {
      ackedAt: new Date()
    }
  });

  return res.json({ acknowledged: result.count });
});

router.post('/', createCardRateLimiter, async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const parsed = validateCardPayload(req.body ?? {});
  if (!parsed.ok || !parsed.value) {
    return res.status(400).json({ message: 'Invalid card payload', errors: parsed.errors });
  }
  const { data } = req.body as Record<string, unknown>;
  if (!isOptionalString(data, 4096)) {
    return res.status(400).json({ message: 'data contains invalid value' });
  }

  const created = await prisma.card.create({
    data: {
      accountId,
      uid: parsed.value.uid!,
      atr: parsed.value.atr,
      data: (data as string | undefined) || null,
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
  const { data } = req.body as Record<string, unknown>;
  if (!isOptionalString(data, 4096)) {
    return res.status(400).json({ message: 'data contains invalid value' });
  }

  const updated = await prisma.card.update({
    where: { id: card.id },
    data: {
      uid: parsed.value.uid ?? card.uid,
      atr: parsed.value.atr ?? card.atr,
      data: (data as string | undefined) ?? card.data,
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
  if (card) {
    await prisma.card.delete({ where: { id: card.id } });
    return res.status(204).send();
  }

  const cloudCard = await prisma.cloudCard.findFirst({ where: { id: req.params.id, accountId, deletedAt: null } });
  if (!cloudCard) {
    return res.status(404).json({ message: 'Card not found' });
  }

  await prisma.cloudCard.update({
    where: { id: cloudCard.id },
    data: { deletedAt: new Date() }
  });
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
