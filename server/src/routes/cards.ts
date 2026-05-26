import { Router } from 'express';
import { v4 as uuidv4 } from 'uuid';
import prisma from '../db.js';
import { RELAY_TOKEN_TTL_MINUTES } from '../config.js';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';
import { sseHub } from '../services/sseHub.js';
import { asyncHandler, HttpError } from '../utils/http.js';
import {
  optionalEnum,
  optionalHexArray,
  optionalHexString,
  optionalString,
  requireObject
} from '../utils/validation.js';

const router = Router();
router.use(authMiddleware);
const cardTypes = ['MIFARE', 'EMV', 'FELICA', 'UNKNOWN'] as const;

router.get('/', asyncHandler(async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const cards = await prisma.card.findMany({
    where: { accountId },
    orderBy: { createdAt: 'desc' }
  });
  return res.json({ data: cards });
}));

router.post('/', asyncHandler(async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const body = requireObject(req.body);
  const uid = optionalHexString(body.uid, 'uid', 64);
  if (!uid) {
    throw new HttpError(400, 'uid is required');
  }
  const type = optionalEnum(body.type, 'type', cardTypes) || 'UNKNOWN';
  const aidList = optionalHexArray(body.aidList, 'aidList', 64);
  const created = await prisma.card.create({
    data: {
      accountId,
      uid,
      atqa: optionalHexString(body.atqa, 'atqa', 8),
      sak: optionalHexString(body.sak, 'sak', 8),
      ats: optionalHexString(body.ats, 'ats', 512),
      historicalBytes: optionalHexString(body.historicalBytes, 'historicalBytes', 512),
      label: optionalString(body.label, 'label', { maxLength: 120 }),
      type,
      aidList: aidList ? JSON.stringify(aidList) : null
    }
  });
  sseHub.sendToUser(accountId, 'cards_changed', { cardId: created.id, action: 'created' });
  return res.status(201).json(created);
}));

router.get('/:id', asyncHandler(async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const card = await prisma.card.findFirst({ where: { id: req.params.id, accountId } });
  if (!card) throw new HttpError(404, 'Card not found');
  return res.json(card);
}));

router.put('/:id', asyncHandler(async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const card = await prisma.card.findFirst({ where: { id: req.params.id, accountId } });
  if (!card) throw new HttpError(404, 'Card not found');
  const body = requireObject(req.body);
  const aidList = body.aidList === undefined ? undefined : optionalHexArray(body.aidList, 'aidList', 64);

  const updated = await prisma.card.update({
    where: { id: card.id },
    data: {
      uid: body.uid !== undefined ? optionalHexString(body.uid, 'uid', 64) ?? card.uid : card.uid,
      atqa: body.atqa !== undefined ? optionalHexString(body.atqa, 'atqa', 8) ?? null : card.atqa,
      sak: body.sak !== undefined ? optionalHexString(body.sak, 'sak', 8) ?? null : card.sak,
      ats: body.ats !== undefined ? optionalHexString(body.ats, 'ats', 512) ?? null : card.ats,
      historicalBytes: body.historicalBytes !== undefined
        ? optionalHexString(body.historicalBytes, 'historicalBytes', 512) ?? null
        : card.historicalBytes,
      label: body.label !== undefined ? optionalString(body.label, 'label', { maxLength: 120 }) ?? null : card.label,
      type: body.type !== undefined ? optionalEnum(body.type, 'type', cardTypes) ?? card.type : card.type,
      aidList: body.aidList !== undefined ? (aidList ? JSON.stringify(aidList) : null) : card.aidList
    }
  });

  sseHub.sendToUser(accountId, 'cards_changed', { cardId: updated.id, action: 'updated' });
  return res.json(updated);
}));

router.delete('/:id', asyncHandler(async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const card = await prisma.card.findFirst({ where: { id: req.params.id, accountId } });
  if (!card) throw new HttpError(404, 'Card not found');

  await prisma.card.delete({ where: { id: card.id } });
  sseHub.sendToUser(accountId, 'cards_changed', { cardId: card.id, action: 'deleted' });
  return res.status(204).send();
}));

router.post('/:id/relay-token', asyncHandler(async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const card = await prisma.card.findFirst({ where: { id: req.params.id, accountId } });
  if (!card) throw new HttpError(404, 'Card not found');

  const token = uuidv4();
  const expiresAt = new Date(Date.now() + RELAY_TOKEN_TTL_MINUTES * 60_000);

  await prisma.card.update({
    where: { id: card.id },
    data: { relayToken: token, relayTokenExpiresAt: expiresAt }
  });

  sseHub.sendToUser(accountId, 'cards_changed', { cardId: card.id, action: 'token_generated' });
  return res.json({ token, expiresAt });
}));

export default router;
