import { Router } from 'express';
import bcrypt from 'bcrypt';
import prisma from '../db.js';
import { authMiddleware, createAccessToken, createRefreshToken, type AuthenticatedRequest } from '../middleware/auth.js';

const router = Router();

router.post('/register', async (req, res) => {
  const { email, password, name, accountName } = req.body as {
    email?: string;
    password?: string;
    name?: string;
    accountName?: string;
  };

  if (!email || !password) {
    return res.status(400).json({ message: 'email and password are required' });
  }

  const existing = await prisma.user.findUnique({ where: { email } });
  if (existing) {
    return res.status(409).json({ message: 'Email already registered' });
  }

  const hashedPassword = await bcrypt.hash(password, 10);
  const created = await prisma.account.create({
    data: {
      name: accountName || name || email,
      users: {
        create: {
          email,
          password: hashedPassword,
          name,
          role: 'ADMIN'
        }
      }
    },
    include: { users: true }
  });

  const user = created.users[0];
  const authUser = { id: user.id, accountId: user.accountId, role: user.role, email: user.email };
  const accessToken = createAccessToken(authUser);
  const refreshToken = createRefreshToken(authUser);
  return res.status(201).json({
    token: accessToken,
    accessToken,
    refreshToken,
    user: { id: user.id, email: user.email, role: user.role, name: user.name }
  });
});

router.post('/login', async (req, res) => {
  const { email, password } = req.body as { email?: string; password?: string };
  if (!email || !password) {
    return res.status(400).json({ message: 'email and password are required' });
  }

  const user = await prisma.user.findUnique({ where: { email } });
  if (!user) {
    return res.status(401).json({ message: 'Invalid credentials' });
  }

  const ok = await bcrypt.compare(password, user.password);
  if (!ok) {
    return res.status(401).json({ message: 'Invalid credentials' });
  }

  const authUser = { id: user.id, accountId: user.accountId, role: user.role, email: user.email };
  const accessToken = createAccessToken(authUser);
  const refreshToken = createRefreshToken(authUser);
  return res.json({
    token: accessToken,
    accessToken,
    refreshToken,
    user: { id: user.id, email: user.email, role: user.role, name: user.name }
  });
});

router.get('/me', authMiddleware, async (req, res) => {
  const authReq = req as AuthenticatedRequest;
  if (!authReq.user) {
    return res.status(401).json({ message: 'Unauthorized' });
  }

  const user = await prisma.user.findUnique({
    where: { id: authReq.user.id },
    include: { account: { select: { id: true, name: true } } }
  });

  if (!user) {
    return res.status(404).json({ message: 'User not found' });
  }

  return res.json({
    user: {
      id: user.id,
      email: user.email,
      name: user.name,
      role: user.role,
      account: user.account
    }
  });
});

export default router;
