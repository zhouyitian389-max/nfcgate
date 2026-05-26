import type { NextFunction, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import prisma from '../db.js';
import { JWT_EXPIRES_IN, JWT_SECRET } from '../config.js';
import { HttpError } from '../utils/http.js';

export interface AuthUser {
  id: string;
  accountId: string;
  role: string;
  email: string;
}

export interface AuthenticatedRequest extends Request {
  user?: AuthUser;
}

export function createAccessToken(user: AuthUser): string {
  return jwt.sign(user, JWT_SECRET, { expiresIn: JWT_EXPIRES_IN });
}

export function extractBearerToken(authHeader?: string): string | null {
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return null;
  }
  return authHeader.slice('Bearer '.length).trim();
}

export async function authenticateAccessToken(token: string): Promise<AuthUser> {
  try {
    const payload = jwt.verify(token, JWT_SECRET) as AuthUser;
    const user = await prisma.user.findUnique({
      where: { id: payload.id },
      select: { id: true, accountId: true, role: true, email: true }
    });

    if (!user) {
      throw new HttpError(401, 'Invalid token user');
    }

    return user;
  } catch {
    throw new HttpError(401, 'Invalid or expired token');
  }
}

export async function authMiddleware(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  const token = extractBearerToken(req.headers.authorization);
  if (!token) {
    return res.status(401).json({ message: 'Missing authorization token' });
  }

  try {
    req.user = await authenticateAccessToken(token);
    return next();
  } catch (error) {
    const message = error instanceof HttpError ? error.message : 'Invalid or expired token';
    return res.status(401).json({ message });
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
