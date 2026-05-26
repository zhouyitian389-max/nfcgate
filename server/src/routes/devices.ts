import { Router } from 'express';
import prisma from '../db.js';
import { authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';

const router = Router();
router.use(authMiddleware);

router.get('/', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const devices = await prisma.device.findMany({ where: { accountId }, orderBy: { updatedAt: 'desc' } });
  return res.json({ data: devices });
});

router.post('/register', async (req, res) => {
  const { accountId } = (req as AuthenticatedRequest).user!;
  const { deviceId, name, type, model, osVersion } = req.body as {
    deviceId?: string;
    name?: string;
    type?: string;
    model?: string;
    osVersion?: string;
  };

  if (!deviceId) return res.status(400).json({ message: 'deviceId is required' });

  const device = await prisma.device.upsert({
    where: { accountId_deviceId: { accountId, deviceId } },
    update: { name, type, model, osVersion, online: true, lastSeen: new Date() },
    create: { accountId, deviceId, name, type: type || 'HCE', model, osVersion, online: true, lastSeen: new Date() }
  });

  return res.status(201).json(device);
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
