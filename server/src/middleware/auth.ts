import type { NextFunction, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import prisma from '../db.js';
import { isBlacklisted } from '../services/tokenBlacklist.js';

export interface AuthUser {
  id: string;
  accountId: string;
  role: string;
  email: string;
}

export interface AuthenticatedRequest extends Request {
  user?: AuthUser;
}

const DEFAULT_JWT_SECRET = 'dev-secret-change-me';
const DEFAULT_JWT_REFRESH_SECRET = 'dev-refresh-secret-change-me';
const MIN_SECRET_LENGTH = 32;

function resolveSecret(envName: 'JWT_SECRET' | 'JWT_REFRESH_SECRET', fallback: string) {
  const value = process.env[envName] || fallback;
  if (process.env.NODE_ENV === 'production') {
    if (!process.env[envName] || value === fallback || value.length < MIN_SECRET_LENGTH) {
      throw new Error(`${envName} must be set to a random secret with at least ${MIN_SECRET_LENGTH} characters in production`);
    }
  }
  return value;
}

const JWT_SECRET = resolveSecret('JWT_SECRET', DEFAULT_JWT_SECRET);
const JWT_REFRESH_SECRET = resolveSecret('JWT_REFRESH_SECRET', DEFAULT_JWT_REFRESH_SECRET);

export function createAccessToken(user: AuthUser): string {
  const expiresIn = (process.env.JWT_EXPIRES_IN || '7d') as jwt.SignOptions['expiresIn'];
  return jwt.sign(user, JWT_SECRET, { expiresIn });
}

export function createRefreshToken(user: AuthUser): string {
  const expiresIn = (process.env.JWT_REFRESH_EXPIRES_IN || '30d') as jwt.SignOptions['expiresIn'];
  return jwt.sign(user, JWT_REFRESH_SECRET, { expiresIn });
}

export function verifyRefreshToken(token: string): AuthUser {
  return jwt.verify(token, JWT_REFRESH_SECRET) as AuthUser;
}

export async function authMiddleware(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ message: 'Missing Bearer token' });
  }

  const token = authHeader.slice('Bearer '.length);
  if (isBlacklisted(token)) {
    return res.status(401).json({ message: 'Token has been revoked' });
  }

  try {
    const payload = jwt.verify(token, JWT_SECRET) as AuthUser;
    const user = await prisma.user.findUnique({
      where: { id: payload.id },
      select: { id: true, accountId: true, role: true, email: true }
    });

    if (!user) {
      return res.status(401).json({ message: 'Invalid token user' });
    }

    req.user = user;
    return next();
  } catch {
    return res.status(401).json({ message: 'Invalid or expired token' });
  }
}

export function adminOnly(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  if (!req.user) {
    return res.status(401).json({ message: 'Unauthorized' });
  }
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ message: 'Admin only' });
  }
  return next();
}
