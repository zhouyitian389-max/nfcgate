import { Router } from 'express';
import prisma from '../db.js';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';
import { bindSessionToDevice, revokeDeviceSessions } from '../services/authSessions.js';

const router = Router();
router.use(authMiddleware);

function trimString(value: unknown, maxLength: number) {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  return trimmed.slice(0, maxLength);
}

function normalizeDeviceType(value: unknown) {
  const next = trimString(value, 32)?.toUpperCase();
  if (!next) return 'HCE';
  return ['HCE', 'READER', 'EXTERNAL_READER'].includes(next) ? next : 'HCE';
}

async function upsertDevice(accountId: string, body: Record<string, unknown>) {
  const deviceId = trimString(body.deviceId ?? body.device_id, 128);
  const name = trimString(body.name ?? body.deviceName ?? body.device_name, 128);
  const type = normalizeDeviceType(body.type);
  const model = trimString(body.model, 128);
  const osVersion = trimString(body.osVersion ?? body.os_version, 128);
  const fcmToken = trimString(body.fcmToken ?? body.fcm_token, 512);

  if (!deviceId) {
    throw new Error('deviceId is required');
  }

  return prisma.device.upsert({
    where: { accountId_deviceId: { accountId, deviceId } },
    update: {
      name,
      type,
      model,
      osVersion,
      fcmToken,
      online: true,
      lastSeen: new Date()
    },
    create: {
      accountId,
      deviceId,
      name,
      type,
      model,
      osVersion,
      fcmToken,
      online: true,
      lastSeen: new Date()
    }
  });
}

function toLegacyDevice(device: {
  id: string;
  deviceId: string;
  name: string | null;
  model: string | null;
  lastSeen: Date;
  online: boolean;
}) {
  return {
    id: device.id,
    device_id: device.deviceId,
    device_name: device.name || device.model || device.deviceId,
    last_active: device.lastSeen.toISOString(),
    online: device.online
  };
}

router.get('/', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const devices = await prisma.device.findMany({ where: { accountId }, orderBy: { updatedAt: 'desc' } });
  return res.json({
    data: devices,
    devices: devices.map(toLegacyDevice)
  });
});

router.get('/list', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const devices = await prisma.device.findMany({ where: { accountId }, orderBy: { updatedAt: 'desc' } });
  return res.json({ devices: devices.map(toLegacyDevice) });
});

router.get('/:id', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const device = await prisma.device.findFirst({ where: { id: req.params.id, accountId } });
  if (!device) return res.status(404).json({ message: 'Device not found' });
  return res.json(device);
});

router.post('/register', async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  try {
    const device = await upsertDevice(user.accountId, req.body as Record<string, unknown>);
    await bindSessionToDevice(user.sessionId, device.deviceId, device.name);
    return res.status(201).json({
      ...device,
      device_id: device.deviceId,
      device_name: device.name || device.model || device.deviceId
    });
  } catch (error) {
    return res.status(400).json({ message: error instanceof Error ? error.message : 'Invalid device payload' });
  }
});

router.post('/fcm', async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const fcmToken = trimString(req.body?.fcm_token ?? req.body?.fcmToken, 512);
  if (!fcmToken) {
    return res.status(400).json({ message: 'fcm_token is required' });
  }
  if (!user.deviceId) {
    return res.status(202).json({ message: 'No bound device for current session' });
  }

  await prisma.device.updateMany({
    where: { accountId: user.accountId, deviceId: user.deviceId },
    data: {
      fcmToken,
      online: true,
      lastSeen: new Date()
    }
  });
  return res.json({ message: 'FCM token registered' });
});

router.post('/logout', async (req, res) => {
  const user = (req as AuthenticatedRequest).user!;
  const deviceId = trimString(req.body?.device_id ?? req.body?.deviceId, 128);
  if (!deviceId) {
    return res.status(400).json({ message: 'device_id is required' });
  }

  const device = await prisma.device.findFirst({ where: { accountId: user.accountId, deviceId } });
  if (!device) {
    return res.status(404).json({ message: 'Device not found' });
  }

  await prisma.device.update({
    where: { id: device.id },
    data: { online: false }
  });
  await revokeDeviceSessions(user.id, deviceId, 'Logged out from device list');

  return res.json({ message: 'Device logged out', device_id: deviceId });
});

router.put('/:id', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const device = await prisma.device.findFirst({ where: { id: req.params.id, accountId } });
  if (!device) return res.status(404).json({ message: 'Device not found' });

  const updated = await prisma.device.update({
    where: { id: device.id },
    data: {
      name: trimString(req.body?.name, 128) ?? device.name,
      type: normalizeDeviceType(req.body?.type ?? device.type),
      model: trimString(req.body?.model, 128) ?? device.model,
      osVersion: trimString(req.body?.osVersion, 128) ?? device.osVersion,
      online: typeof req.body?.online === 'boolean' ? req.body.online : device.online
    }
  });

  return res.json(updated);
});

router.post('/:id/heartbeat', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const device = await prisma.device.findFirst({ where: { id: req.params.id, accountId } });
  if (!device) return res.status(404).json({ message: 'Device not found' });

  const updated = await prisma.device.update({
    where: { id: device.id },
    data: { online: true, lastSeen: new Date() }
  });

  return res.json(updated);
});

router.delete('/:id', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const device = await prisma.device.findFirst({ where: { id: req.params.id, accountId } });
  if (!device) return res.status(404).json({ message: 'Device not found' });

  await prisma.device.delete({ where: { id: device.id } });
  return res.status(204).send();
});

export default router;
