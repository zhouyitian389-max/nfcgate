import { FastifyInstance } from 'fastify';
import bcrypt from 'bcryptjs';
import { prisma } from '../db.js';
import { requireAdmin } from '../middleware/auth.js';

export async function adminRoutes(app: FastifyInstance) {
  app.addHook('preHandler', requireAdmin);

  // GET /api/admin/users
  app.get('/users', async (request) => {
    const { page = '1', limit = '20' } = request.query as any;
    const skip = (parseInt(page) - 1) * parseInt(limit);
    const take = parseInt(limit);

    const [data, total] = await Promise.all([
      prisma.account.findMany({
        skip,
        take,
        orderBy: { createdAt: 'desc' },
        select: { id: true, email: true, name: true, role: true, createdAt: true },
      }),
      prisma.account.count(),
    ]);
    return { data, total };
  });

  // POST /api/admin/users — 开户
  app.post('/users', async (request, reply) => {
    const { email, password, name, role } = request.body as any;

    const existing = await prisma.account.findUnique({ where: { email } });
    if (existing) {
      return reply.status(409).send({ message: 'Email already exists' });
    }

    const hashedPassword = await bcrypt.hash(password, 10);
    const account = await prisma.account.create({
      data: { email, password: hashedPassword, name, role: role || 'USER' },
    });
    return { id: account.id, email: account.email, name: account.name, role: account.role };
  });

  // DELETE /api/admin/users/:id
  app.delete('/users/:id', async (request, reply) => {
    const { id } = request.params as any;
    const account = await prisma.account.findUnique({ where: { id } });
    if (!account) return reply.status(404).send({ message: 'User not found' });

    await prisma.account.delete({ where: { id } });
    return { message: 'Deleted' };
  });

  // GET /api/admin/cards
  app.get('/cards', async (request) => {
    const { page = '1', limit = '20' } = request.query as any;
    const skip = (parseInt(page) - 1) * parseInt(limit);
    const take = parseInt(limit);

    const [data, total] = await Promise.all([
      prisma.card.findMany({
        skip,
        take,
        orderBy: { createdAt: 'desc' },
        include: { account: { select: { email: true, name: true } } },
      }),
      prisma.card.count(),
    ]);
    return { data, total };
  });

  // GET /api/admin/logs
  app.get('/logs', async (request) => {
    const { page = '1', limit = '50' } = request.query as any;
    const skip = (parseInt(page) - 1) * parseInt(limit);
    const take = parseInt(limit);

    const [data, total] = await Promise.all([
      prisma.operationLog.findMany({
        skip,
        take,
        orderBy: { createdAt: 'desc' },
        include: { account: { select: { email: true, name: true } } },
      }),
      prisma.operationLog.count(),
    ]);
    return { data, total };
  });
}
