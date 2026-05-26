import { Router } from 'express';
import bcrypt from 'bcryptjs';
import prisma from '../db.js';
import { AUTH_RATE_LIMIT_MAX, AUTH_RATE_LIMIT_WINDOW_MS, BCRYPT_ROUNDS } from '../config.js';
import { createRateLimiter } from '../middleware/rateLimit.js';
import { authMiddleware, createAccessToken, type AuthenticatedRequest } from '../middleware/auth.js';
import { asyncHandler, HttpError } from '../utils/http.js';
import { optionalString, requireEmail, requireObject, requireString } from '../utils/validation.js';

const router = Router();
const authRateLimiter = createRateLimiter({
  windowMs: AUTH_RATE_LIMIT_WINDOW_MS,
  max: AUTH_RATE_LIMIT_MAX,
  keyGenerator: (req) => `${req.ip}:${String(req.body?.email || 'anonymous').toLowerCase()}`
});

router.post('/register', authRateLimiter, asyncHandler(async (req, res) => {
  const body = requireObject(req.body);
  const email = requireEmail(body.email);
  const password = requireString(body.password, 'password', { minLength: 8, maxLength: 128 });
  const name = optionalString(body.name, 'name', { maxLength: 120 });
  const accountName = optionalString(body.accountName, 'accountName', { maxLength: 120 });

  const existing = await prisma.user.findUnique({ where: { email } });
  if (existing) {
    throw new HttpError(409, 'Email already registered');
  }

  const hashedPassword = await bcrypt.hash(password, BCRYPT_ROUNDS);
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
  const token = createAccessToken({ id: user.id, accountId: user.accountId, role: user.role, email: user.email });
  return res.status(201).json({ token, user: { id: user.id, email: user.email, role: user.role, name: user.name } });
}));

router.post('/login', authRateLimiter, asyncHandler(async (req, res) => {
  const body = requireObject(req.body);
  const email = requireEmail(body.email);
  const password = requireString(body.password, 'password', { minLength: 8, maxLength: 128 });

  const user = await prisma.user.findUnique({ where: { email } });
  if (!user) {
    throw new HttpError(401, 'Invalid credentials');
  }

  const ok = await bcrypt.compare(password, user.password);
  if (!ok) {
    throw new HttpError(401, 'Invalid credentials');
  }

  const token = createAccessToken({ id: user.id, accountId: user.accountId, role: user.role, email: user.email });
  return res.json({ token, user: { id: user.id, email: user.email, role: user.role, name: user.name } });
}));

router.get('/me', authMiddleware, asyncHandler(async (req, res) => {
  const authReq = req as AuthenticatedRequest;
  if (!authReq.user) {
    throw new HttpError(401, 'Unauthorized');
  }

  const user = await prisma.user.findUnique({
    where: { id: authReq.user.id },
    include: { account: { select: { id: true, name: true } } }
  });

  if (!user) {
    throw new HttpError(404, 'User not found');
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
}));

export default router;
