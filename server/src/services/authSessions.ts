import crypto from 'crypto';
import prisma from '../db.js';
import { createAccessToken, createRefreshToken, type SessionTokenUser } from './tokenService.js';

interface SessionContext {
  deviceId?: string | null;
  deviceName?: string | null;
  ip?: string | null;
  userAgent?: string | null;
}

interface SessionUser extends SessionTokenUser {
  passwordChangedAt?: Date | null;
}

function trimValue(value: string | null | undefined, maxLength: number): string | null {
  if (!value) return null;
  const trimmed = value.trim();
  if (!trimmed) return null;
  return trimmed.slice(0, maxLength);
}

export function hashToken(token: string): string {
  return crypto.createHash('sha256').update(token).digest('hex');
}

export function tokenHashMatches(token: string, expectedHash: string | null | undefined): boolean {
  if (!expectedHash) return false;
  const actualHash = hashToken(token);
  return crypto.timingSafeEqual(Buffer.from(actualHash), Buffer.from(expectedHash));
}

function passwordChangedAtMs(user: SessionUser): number {
  return user.passwordChangedAt?.getTime() ?? 0;
}

export async function createSessionTokens(user: SessionUser, context: SessionContext = {}) {
  const sessionId = crypto.randomUUID();
  const pwdAt = passwordChangedAtMs(user);
  const accessToken = createAccessToken(user, sessionId, pwdAt);
  const refreshToken = createRefreshToken(user, sessionId, pwdAt);

  await prisma.authSession.create({
    data: {
      sessionId,
      userId: user.id,
      refreshTokenHash: hashToken(refreshToken),
      deviceId: trimValue(context.deviceId, 128),
      deviceName: trimValue(context.deviceName, 128),
      userAgent: trimValue(context.userAgent, 255),
      lastIp: trimValue(context.ip, 128),
      lastSeen: new Date()
    }
  });

  return { sessionId, accessToken, refreshToken };
}

export async function rotateSessionTokens(sessionId: string, user: SessionUser, context: SessionContext = {}) {
  const pwdAt = passwordChangedAtMs(user);
  const accessToken = createAccessToken(user, sessionId, pwdAt);
  const refreshToken = createRefreshToken(user, sessionId, pwdAt);

  await prisma.authSession.updateMany({
    where: { sessionId, userId: user.id, revokedAt: null },
    data: {
      refreshTokenHash: hashToken(refreshToken),
      deviceId: trimValue(context.deviceId, 128) ?? undefined,
      deviceName: trimValue(context.deviceName, 128) ?? undefined,
      userAgent: trimValue(context.userAgent, 255) ?? undefined,
      lastIp: trimValue(context.ip, 128) ?? undefined,
      lastSeen: new Date()
    }
  });

  return { sessionId, accessToken, refreshToken };
}

export async function touchSession(sessionId: string, context: SessionContext = {}) {
  await prisma.authSession.updateMany({
    where: { sessionId, revokedAt: null },
    data: {
      deviceId: trimValue(context.deviceId, 128) ?? undefined,
      deviceName: trimValue(context.deviceName, 128) ?? undefined,
      userAgent: trimValue(context.userAgent, 255) ?? undefined,
      lastIp: trimValue(context.ip, 128) ?? undefined,
      lastSeen: new Date()
    }
  });
}

export async function bindSessionToDevice(sessionId: string, deviceId: string, deviceName?: string | null) {
  await prisma.authSession.updateMany({
    where: { sessionId, revokedAt: null },
    data: {
      deviceId: trimValue(deviceId, 128) ?? undefined,
      deviceName: trimValue(deviceName, 128) ?? undefined,
      lastSeen: new Date()
    }
  });
}

export async function revokeSession(sessionId: string, reason: string) {
  await prisma.authSession.updateMany({
    where: { sessionId, revokedAt: null },
    data: {
      revokedAt: new Date(),
      revokeReason: trimValue(reason, 255) ?? 'revoked'
    }
  });
}

export async function revokeUserSessions(userId: string, reason: string, exceptSessionId?: string) {
  await prisma.authSession.updateMany({
    where: {
      userId,
      revokedAt: null,
      ...(exceptSessionId ? { NOT: { sessionId: exceptSessionId } } : {})
    },
    data: {
      revokedAt: new Date(),
      revokeReason: trimValue(reason, 255) ?? 'revoked'
    }
  });
}

export async function revokeDeviceSessions(userId: string, deviceId: string, reason: string) {
  await prisma.authSession.updateMany({
    where: {
      userId,
      deviceId,
      revokedAt: null
    },
    data: {
      revokedAt: new Date(),
      revokeReason: trimValue(reason, 255) ?? 'revoked'
    }
  });
}
