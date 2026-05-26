import { Router } from 'express';
import prisma from '../db.js';

const router = Router();

router.get('/live', (_req, res) => {
  return res.status(200).json({
    status: 'ok',
    service: 'nfcgate-server',
    timestamp: new Date().toISOString()
  });
});

router.get('/ready', async (_req, res) => {
  try {
    await prisma.$queryRaw`SELECT 1`;
    return res.status(200).json({
      status: 'ready',
      checks: {
        database: 'ok'
      },
      timestamp: new Date().toISOString()
    });
  } catch {
    return res.status(503).json({
      status: 'not_ready',
      checks: {
        database: 'error'
      },
      timestamp: new Date().toISOString()
    });
  }
});

export default router;
