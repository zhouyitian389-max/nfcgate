import crypto from 'crypto';
import prisma from '../db.js';

const blacklist = new Map<string, number>();

function hashToken(token: string) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

export async function addToBlacklist(token: string, expiresAt: number): Promise<void> {
  const tokenHash = hashToken(token);
  blacklist.set(tokenHash, expiresAt);
  await prisma.revokedToken.upsert({
    where: { tokenHash },
    update: {
      expiresAt: new Date(expiresAt),
      revokedAt: new Date()
    },
    create: {
      tokenHash,
      expiresAt: new Date(expiresAt)
    }
  });
}

export async function isBlacklisted(token: string): Promise<boolean> {
  const tokenHash = hashToken(token);
  const cachedExpiry = blacklist.get(tokenHash);
  if (cachedExpiry !== undefined) {
    if (Date.now() < cachedExpiry) {
      return true;
    }
    blacklist.delete(tokenHash);
  }

  const entry = await prisma.revokedToken.findUnique({
    where: { tokenHash },
    select: { expiresAt: true }
  });
  if (!entry) return false;

  const expiry = entry.expiresAt.getTime();
  if (Date.now() > expiry) {
    blacklist.delete(tokenHash);
    await prisma.revokedToken.delete({ where: { tokenHash } }).catch(() => undefined);
    return false;
  }
  blacklist.set(tokenHash, expiry);
  return true;
}

setInterval(() => {
  const now = Date.now();
  for (const [tokenHash, expiry] of blacklist.entries()) {
    if (now > expiry) blacklist.delete(tokenHash);
  }

  void prisma.revokedToken.deleteMany({
    where: { expiresAt: { lt: new Date(now) } }
  }).catch(() => undefined);
}, 5 * 60 * 1000).unref();
