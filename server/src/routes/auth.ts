import { Router } from 'express';
import bcrypt from 'bcrypt';
import rateLimit from 'express-rate-limit';
import prisma from '../db.js';
import { adminOnly, authMiddleware, type AuthenticatedRequest } from '../middleware/auth.js';
import { loginRateLimiter, registerRateLimiter } from '../middleware/rateLimit.js';
import { createSessionTokens, revokeSession, revokeUserSessions, rotateSessionTokens, tokenHashMatches } from '../services/authSessions.js';
import { addToBlacklist } from '../services/tokenBlacklist.js';
import { decodeToken, getAccessTokenExpiresInMs, getRefreshTokenExpiresInMs, getTokenExpiresInSeconds, verifyRefreshToken, type SessionTokenUser } from '../services/tokenService.js';
import { isStrongPassword, isValidEmail, normalizeEmail } from '../utils/validators.js';
import { closeSessionsByAccountId } from '../ws/relay.js';

const router = Router();
const authLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: Number(process.env.AUTH_RATE_LIMIT_MAX || 30),
  standardHeaders: true,
  legacyHeaders: false
});

interface LoginBody {
  email?: string;
  password?: string;
  deviceId?: string;
  deviceName?: string;
}

function trimValue(value: unknown, maxLength: number): string | undefined {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  return trimmed.slice(0, maxLength);
}

function validatePassword(password: unknown): password is string {
  return typeof password === 'string' && password.length >= 8 && password.length <= 256;
}

async function resetLoginLock(userId: string, loginAttempts: number, lockedUntil: Date | null) {
  if (loginAttempts !== 0 || lockedUntil) {
    await prisma.user.update({
      where: { id: userId },
      data: {
        loginAttempts: 0,
        lockedUntil: null
      }
    });
  }
}

async function markFailedLogin(userId: string, loginAttempts: number) {
  const nextAttempts = loginAttempts + 1;
  await prisma.user.update({
    where: { id: userId },
    data: {
      loginAttempts: nextAttempts,
      lockedUntil: nextAttempts >= 5 ? new Date(Date.now() + 30 * 60 * 1000) : null
    }
  });
}

async function resolveLoginUser(email: string | undefined) {
  if (!email) return null;
  return prisma.user.findUnique({
    where: { email },
    include: { account: { select: { id: true, name: true, encryptionSalt: true } } }
  });
}

function buildSessionUser(user: {
  id: string;
  accountId: string;
  role: string;
  email: string;
  passwordChangedAt?: Date | null;
}): SessionTokenUser & { passwordChangedAt?: Date | null } {
  return {
    id: user.id,
    accountId: user.accountId,
    role: user.role,
    email: user.email,
    passwordChangedAt: user.passwordChangedAt ?? null
  };
}

function authPayloadResponse(user: {
  id: string;
  email: string;
  role: string;
  name?: string | null;
  mustChangePassword?: boolean;
  account: { id: string; name: string; encryptionSalt: string };
}, accessToken: string, refreshToken: string) {
  const expiresInSeconds = getTokenExpiresInSeconds(accessToken);
  const refreshTokenExpiresIn = Math.floor(getRefreshTokenExpiresInMs() / 1000);
  return {
    token: accessToken,
    accessToken,
    refreshToken,
    expiresAt: Date.now() + getAccessTokenExpiresInMs(),
    expires_in: expiresInSeconds,
    expiresIn: expiresInSeconds,
    tokenType: 'Bearer',
    refreshTokenExpiresIn,
    account_id: user.account.id,
    salt: user.account.encryptionSalt,
    mustChangePassword: Boolean(user.mustChangePassword),
    user: {
      id: user.id,
      email: user.email,
      role: user.role,
      name: user.name,
      account: {
        id: user.account.id,
        name: user.account.name
      }
    }
  };
}

router.post('/register', registerRateLimiter, authMiddleware, adminOnly, async (req, res) => {
  const admin = req as AuthenticatedRequest;
  const { email, password, name, accountName } = req.body as {
    email?: string;
    password?: string;
    name?: string;
    accountName?: string;
  };
  const normalizedEmail = normalizeEmail(email);

  if (!normalizedEmail || !isValidEmail(normalizedEmail)) {
    return res.status(400).json({ message: 'A valid email address is required' });
  }

  if (!isStrongPassword(password)) {
    return res.status(400).json({ message: 'Password must be 8-256 chars and include an uppercase letter, number, and special character' });
  }

  const existing = await prisma.user.findUnique({ where: { email: normalizedEmail } });
  if (existing) {
    return res.status(409).json({ message: 'Email already registered' });
  }

  const hashedPassword = await bcrypt.hash(password, 12);
  const user = await prisma.user.create({
    data: {
      email: normalizedEmail,
      password: hashedPassword,
      name: trimValue(name, 120),
      role: 'USER',
      accountId: admin.user!.accountId,
      passwordChangedAt: new Date(),
      mustChangePassword: false
    }
  });
  const account = await prisma.account.findUnique({
    where: { id: admin.user!.accountId },
    select: { id: true, name: true }
  });

  return res.status(201).json({
    message: 'User created',
    user: {
      id: user.id,
      email: user.email,
      name: user.name,
      role: user.role,
      createdAt: user.createdAt,
      account
    },
    accountName: trimValue(accountName, 120) || account?.name || null
  });
});

router.post('/login', loginRateLimiter, async (req, res) => {
  const { email, password, deviceId, deviceName } = req.body as LoginBody;
  const normalizedEmail = normalizeEmail(email);
  if (!normalizedEmail || !isValidEmail(normalizedEmail)) {
    return res.status(400).json({ message: 'email and password are required' });
  }
  if (!validatePassword(password)) {
    return res.status(400).json({ message: 'email and password are required' });
  }

  const user = await resolveLoginUser(normalizedEmail);
  if (!user) {
    return res.status(401).json({ message: 'Invalid credentials' });
  }

  if (user.lockedUntil && user.lockedUntil.getTime() > Date.now()) {
    return res.status(423).json({ message: 'Account is locked. Try again later.' });
  }

  const ok = await bcrypt.compare(password, user.password);
  if (!ok) {
    await markFailedLogin(user.id, user.loginAttempts);
    return res.status(401).json({ message: 'Invalid credentials' });
  }

  await resetLoginLock(user.id, user.loginAttempts, user.lockedUntil ?? null);
  const { accessToken, refreshToken } = await createSessionTokens(buildSessionUser(user), {
    deviceId: trimValue(deviceId, 128),
    deviceName: trimValue(deviceName, 128),
    ip: req.ip,
    userAgent: req.headers['user-agent']
  });

  return res.json(authPayloadResponse(user, accessToken, refreshToken));
});

router.post('/logout', authMiddleware, async (req: AuthenticatedRequest, res) => {
  const authHeader = req.headers.authorization;
  const refreshToken = trimValue((req.body as { refreshToken?: string } | undefined)?.refreshToken, 8192);
  if (authHeader?.startsWith('Bearer ')) {
    const token = authHeader.slice('Bearer '.length);
    const decoded = decodeToken(token);
    const expiry = decoded?.exp ? decoded.exp * 1000 : Date.now() + 7 * 24 * 60 * 60 * 1000;
    await addToBlacklist(token, expiry);
  }
  if (refreshToken) {
    const decodedRefresh = decodeToken(refreshToken);
    const expiry = decodedRefresh?.exp ? decodedRefresh.exp * 1000 : Date.now() + 7 * 24 * 60 * 60 * 1000;
    await addToBlacklist(refreshToken, expiry);
  }

  if (req.user?.sessionId) {
    await revokeSession(req.user.sessionId, 'Logged out');
  }
  if (req.user?.accountId) {
    void closeSessionsByAccountId(req.user.accountId, 'Auth session logged out').catch(() => undefined);
  }
  return res.json({ message: 'Logged out successfully' });
});

router.post('/refresh', authLimiter, async (req, res) => {
  const body = req.body as { refreshToken?: string };
  const providedRefreshToken = trimValue(body.refreshToken, 8192);

  try {
    if (!providedRefreshToken) {
      return res.status(400).json({ message: 'refreshToken is required' });
    }

    const payload = verifyRefreshToken(providedRefreshToken);
    const session = await prisma.authSession.findFirst({
      where: { sessionId: payload.sid, userId: payload.sub, revokedAt: null }
    });
    if (!session || !tokenHashMatches(providedRefreshToken, session.refreshTokenHash)) {
      return res.status(401).json({ message: 'Invalid or revoked refresh token' });
    }

    const user = await prisma.user.findUnique({
      where: { id: payload.sub },
      include: { account: { select: { id: true, name: true, encryptionSalt: true } } }
    });
    if (!user) {
      return res.status(401).json({ message: 'Invalid refresh token user' });
    }
    if ((user.passwordChangedAt?.getTime() ?? 0) > payload.pwd) {
      await revokeSession(session.sessionId, 'Password changed');
      return res.status(401).json({ message: 'Refresh token expired after password change' });
    }

    const { accessToken, refreshToken } = await rotateSessionTokens(session.sessionId, buildSessionUser(user), {
      deviceId: session.deviceId,
      deviceName: session.deviceName,
      ip: req.ip,
      userAgent: req.headers['user-agent']
    });
    return res.json(authPayloadResponse(user, accessToken, refreshToken));
  } catch (error) {
    // Log token refresh errors for monitoring (only in non-production for privacy)
    if (process.env.NODE_ENV !== 'production') {
      console.error('[auth/refresh] Token validation failed:', error instanceof Error ? error.message : 'Unknown error');
    }
    return res.status(401).json({ message: 'Invalid or expired refresh token' });
  }
});

router.post('/change-password', authLimiter, authMiddleware, async (req: AuthenticatedRequest, res) => {
  if (!req.user) {
    return res.status(401).json({ message: 'Unauthorized' });
  }

  const currentPassword = trimValue(req.body?.currentPassword, 256);
  const nextPassword = trimValue(req.body?.newPassword, 256);
  if (!currentPassword || !nextPassword) {
    return res.status(400).json({ message: 'currentPassword and newPassword are required' });
  }
  if (!isStrongPassword(nextPassword)) {
    return res.status(400).json({ message: 'newPassword must include an uppercase letter, number, and special character' });
  }

  const user = await prisma.user.findUnique({
    where: { id: req.user.id },
    include: { account: { select: { id: true, name: true, encryptionSalt: true } } }
  });
  if (!user) {
    return res.status(404).json({ message: 'User not found' });
  }

  const valid = await bcrypt.compare(currentPassword, user.password);
  if (!valid) {
    return res.status(401).json({ message: 'Current password is invalid' });
  }

  const hashedPassword = await bcrypt.hash(nextPassword, 12);
  const changedAt = new Date();
  await prisma.user.update({
    where: { id: user.id },
    data: {
      password: hashedPassword,
      passwordChangedAt: changedAt,
      mustChangePassword: false,
      loginAttempts: 0,
      lockedUntil: null
    }
  });
  await revokeUserSessions(user.id, 'Password changed', req.user.sessionId);
  void closeSessionsByAccountId(user.accountId, 'Password changed, relay sessions terminated').catch(() => undefined);

  const nextUser = { ...user, passwordChangedAt: changedAt };
  const { accessToken, refreshToken } = await rotateSessionTokens(req.user.sessionId, buildSessionUser(nextUser), {
    deviceId: req.user.deviceId,
    ip: req.ip,
    userAgent: req.headers['user-agent']
  });

  return res.json({
    message: 'Password updated',
    ...authPayloadResponse(nextUser, accessToken, refreshToken)
  });
});

router.get('/me', authLimiter, authMiddleware, async (req, res) => {
  const authReq = req as AuthenticatedRequest;
  if (!authReq.user) {
    return res.status(401).json({ message: 'Unauthorized' });
  }

  const user = await prisma.user.findUnique({
    where: { id: authReq.user.id },
    include: { account: { select: { id: true, name: true, encryptionSalt: true } } }
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
      mustChangePassword: user.mustChangePassword,
      sessionId: authReq.user.sessionId,
      account: {
        id: user.account.id,
        name: user.account.name
      }
    }
  });
});

export default router;
