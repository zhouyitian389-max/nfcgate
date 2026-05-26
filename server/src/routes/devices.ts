import { Router } from 'express';
import prisma from '../db.js';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';
import { sseHub } from '../services/sseHub.js';
import { asyncHandler, HttpError } from '../utils/http.js';
import { optionalEnum, optionalString, requireObject, requireString } from '../utils/validation.js';

const router = Router();
router.use(authMiddleware);
const deviceTypes = ['HCE', 'READER', 'EXTERNAL_READER'] as const;

router.get('/', asyncHandler(async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const devices = await prisma.device.findMany({ where: { accountId }, orderBy: { updatedAt: 'desc' } });
  return res.json({ data: devices });
}));

router.post('/register', asyncHandler(async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const body = requireObject(req.body);
  const deviceId = requireString(body.deviceId, 'deviceId', { maxLength: 128 });
  const name = optionalString(body.name, 'name', { maxLength: 120 });
  const type = optionalEnum(body.type, 'type', deviceTypes) || 'HCE';
  const model = optionalString(body.model, 'model', { maxLength: 120 });
  const osVersion = optionalString(body.osVersion, 'osVersion', { maxLength: 64 });

  const device = await prisma.device.upsert({
    where: { accountId_deviceId: { accountId, deviceId } },
    update: { name, type, model, osVersion, online: true, lastSeen: new Date() },
    create: { accountId, deviceId, name, type, model, osVersion, online: true, lastSeen: new Date() }
  });

  sseHub.sendToUser(accountId, 'devices_changed', { deviceId: device.id, action: 'upserted' });
  return res.status(201).json(device);
}));

router.post('/:id/heartbeat', asyncHandler(async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const device = await prisma.device.findFirst({ where: { id: req.params.id, accountId } });
  if (!device) throw new HttpError(404, 'Device not found');

  const updated = await prisma.device.update({
    where: { id: device.id },
    data: { online: true, lastSeen: new Date() }
  });

  sseHub.sendToUser(accountId, 'devices_changed', { deviceId: updated.id, action: 'heartbeat' });
  return res.json(updated);
}));

router.delete('/:id', asyncHandler(async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const device = await prisma.device.findFirst({ where: { id: req.params.id, accountId } });
  if (!device) throw new HttpError(404, 'Device not found');

  await prisma.device.delete({ where: { id: device.id } });
  sseHub.sendToUser(accountId, 'devices_changed', { deviceId: device.id, action: 'deleted' });
  return res.status(204).send();
}));

export default router;
