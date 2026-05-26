import type { Request } from 'express';
import prisma from '../db.js';
import { revokeSession, rotateSessionTokens, tokenHashMatches } from './authSessions.js';
import { getAccessTokenExpiresInMs, getRefreshTokenExpiresInMs, verifyRefreshToken, type SessionTokenUser } from './tokenService.js';

/**
 * Validates a refresh token, checks the blacklist / session state, and
 * rotates both the access token and refresh token.  Returns the new tokens
 * together with an absolute `expiresAt` timestamp (ms since epoch) that
 * the frontend can use to schedule proactive refresh.
 */
export async function refreshAccessToken(
  refreshToken: string,
  req: Request
): Promise<{ accessToken: string; refreshToken: string; expiresAt: number; refreshTokenExpiresIn: number }> {
  const payload = verifyRefreshToken(refreshToken);

  const session = await prisma.authSession.findFirst({
    where: { sessionId: payload.sid, userId: payload.sub, revokedAt: null }
  });

  if (!session || !tokenHashMatches(refreshToken, session.refreshTokenHash)) {
    throw Object.assign(new Error('Invalid or revoked refresh token'), { status: 401 });
  }

  const user = await prisma.user.findUnique({ where: { id: payload.sub } });
  if (!user) {
    throw Object.assign(new Error('User not found'), { status: 401 });
  }

  if ((user.passwordChangedAt?.getTime() ?? 0) > payload.pwd) {
    await revokeSession(session.sessionId, 'Password changed');
    throw Object.assign(new Error('Refresh token expired after password change'), { status: 401 });
  }

  const sessionUser: SessionTokenUser = {
    id: user.id,
    accountId: user.accountId,
    role: user.role,
    email: user.email
  };

  const tokens = await rotateSessionTokens(session.sessionId, sessionUser, {
    deviceId: session.deviceId ?? undefined,
    deviceName: session.deviceName ?? undefined,
    ip: req.ip,
    userAgent: req.headers['user-agent']
  });

  return {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    expiresAt: Date.now() + getAccessTokenExpiresInMs(),
    refreshTokenExpiresIn: Math.floor(getRefreshTokenExpiresInMs() / 1000)
  };
}
