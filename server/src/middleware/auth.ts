import type { NextFunction, Request, Response } from 'express';
import prisma from '../db.js';
import { isBlacklisted } from '../services/tokenBlacklist.js';
import { touchSession } from '../services/authSessions.js';
import { verifyAccessToken } from '../services/tokenService.js';

export interface AuthUser {
  id: string;
  accountId: string;
  role: string;
  email: string;
  sessionId: string;
  deviceId?: string | null;
  mustChangePassword?: boolean;
}

export interface AuthenticatedRequest extends Request {
  user?: AuthUser;
}

export async function authMiddleware(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ message: 'Missing bearer token' });
  }

  const token = authHeader.slice('Bearer '.length);
  if (await isBlacklisted(token)) {
    return res.status(401).json({ message: 'Token has been revoked' });
  }

  try {
    const payload = verifyAccessToken(token);
    const session = await prisma.authSession.findFirst({
      where: { sessionId: payload.sid, revokedAt: null },
      select: {
        sessionId: true,
        deviceId: true,
        user: {
          select: {
            id: true,
            accountId: true,
            role: true,
            email: true,
            mustChangePassword: true,
            passwordChangedAt: true
          }
        }
      }
    });

    if (!session || !session.user || session.user.id !== payload.sub) {
      return res.status(401).json({ message: 'Session has been revoked' });
    }
    const user = session.user;
    if ((user.passwordChangedAt?.getTime() ?? 0) > payload.pwd) {
      return res.status(401).json({ message: 'Session expired after password change' });
    }

    req.user = {
      id: user.id,
      accountId: user.accountId,
      role: user.role,
      email: user.email,
      sessionId: session.sessionId,
      deviceId: session.deviceId,
      mustChangePassword: user.mustChangePassword
    };
    void touchSession(session.sessionId, {
      deviceId: session.deviceId,
      ip: req.ip,
      userAgent: req.headers['user-agent']
    }).catch(() => undefined);
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
