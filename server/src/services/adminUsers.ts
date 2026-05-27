import bcrypt from 'bcrypt';
import { randomUUID } from 'crypto';
import prisma from '../db.js';
import { revokeUserSessions } from './authSessions.js';
import { isStrongPassword, isValidEmail, normalizeEmail } from '../utils/validators.js';

export interface ManagedUserInput {
  email?: unknown;
  password?: unknown;
  name?: unknown;
  accountName?: unknown;
  role?: unknown;
}

export interface ManagedUserPayload {
  email: string;
  password: string;
  name: string | null;
  accountName: string;
  role: 'ADMIN' | 'USER';
}

export interface ManagedUserRecord {
  id: string;
  email: string;
  name: string | null;
  role: string;
  createdAt: Date;
  updatedAt: Date;
  loginAttempts: number;
  lockedUntil: Date | null;
  mustChangePassword: boolean;
  account: {
    id: string;
    name: string;
  };
}

function trimOptionalString(value: unknown, maxLength: number): string | null {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  if (!trimmed) return null;
  return trimmed.slice(0, maxLength);
}

export function validateManagedUserInput(input: ManagedUserInput): { ok: true; value: ManagedUserPayload } | { ok: false; message: string } {
  const email = normalizeEmail(input.email);
  if (!email || !isValidEmail(email)) {
    return { ok: false, message: 'A valid email address is required' };
  }

  if (!isStrongPassword(input.password)) {
    return {
      ok: false,
      message: 'Password must be 8-128 chars and include an uppercase letter, number, and special character'
    };
  }

  const name = trimOptionalString(input.name, 120);
  const accountName = trimOptionalString(input.accountName, 120) ?? name ?? email;
  const role = input.role === 'ADMIN' ? 'ADMIN' : 'USER';

  return {
    ok: true,
    value: {
      email,
      password: input.password,
      name,
      accountName,
      role
    }
  };
}

export async function createManagedUser(input: ManagedUserInput): Promise<ManagedUserRecord> {
  const validation = validateManagedUserInput(input);
  if (!validation.ok) {
    throw new Error(validation.message);
  }

  const existing = await prisma.user.findUnique({ where: { email: validation.value.email } });
  if (existing) {
    throw new Error('Email already registered');
  }

  const hashedPassword = await bcrypt.hash(validation.value.password, 12);
  const createdAt = new Date();
  const user = await prisma.$transaction(async (tx) => {
    const account = await tx.account.create({
      data: {
        name: validation.value.accountName,
        encryptionSalt: randomUUID()
      }
    });

    await tx.user.create({
      data: {
        email: validation.value.email,
        password: hashedPassword,
        name: validation.value.name,
        role: validation.value.role,
        accountId: account.id,
        passwordChangedAt: createdAt,
        mustChangePassword: false
      }
    });

    return tx.user.findUniqueOrThrow({
      where: { email: validation.value.email },
      select: {
        id: true,
        email: true,
        name: true,
        role: true,
        createdAt: true,
        updatedAt: true,
        loginAttempts: true,
        lockedUntil: true,
        mustChangePassword: true,
        account: { select: { id: true, name: true } }
      }
    });
  });

  return user;
}

export async function deleteManagedUser(userId: string) {
  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: { id: true, accountId: true }
  });
  if (!user) {
    throw new Error('User not found');
  }

  const userCount = await prisma.user.count({ where: { accountId: user.accountId } });
  await prisma.$transaction(async (tx) => {
    await tx.authSession.deleteMany({ where: { userId } });
    await tx.user.delete({ where: { id: userId } });

    if (userCount <= 1) {
      await tx.relaySession.deleteMany({ where: { accountId: user.accountId } });
      await tx.apduLog.deleteMany({ where: { accountId: user.accountId } });
      await tx.device.deleteMany({ where: { accountId: user.accountId } });
      await tx.cloudCard.deleteMany({ where: { accountId: user.accountId } });
      await tx.card.deleteMany({ where: { accountId: user.accountId } });
      await tx.account.delete({ where: { id: user.accountId } });
    }
  });

  return user;
}

export async function resetManagedUserPassword(userId: string, password: unknown) {
  if (!isStrongPassword(password)) {
    throw new Error('Password must be 8-128 chars and include an uppercase letter, number, and special character');
  }

  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: { id: true, accountId: true }
  });
  if (!user) {
    throw new Error('User not found');
  }

  const hashedPassword = await bcrypt.hash(password, 12);
  const passwordChangedAt = new Date();
  await prisma.user.update({
    where: { id: userId },
    data: {
      password: hashedPassword,
      passwordChangedAt,
      loginAttempts: 0,
      lockedUntil: null,
      mustChangePassword: false
    }
  });
  await revokeUserSessions(userId, 'Password reset by admin');
  return { user, passwordChangedAt };
}

export async function setManagedUserLock(userId: string, locked: boolean) {
  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: { id: true, accountId: true, lockedUntil: true }
  });
  if (!user) {
    throw new Error('User not found');
  }

  const lockedUntil = locked ? new Date(Date.now() + 3650 * 24 * 60 * 60 * 1000) : null;
  await prisma.user.update({
    where: { id: userId },
    data: {
      loginAttempts: locked ? 5 : 0,
      lockedUntil
    }
  });
  if (locked) {
    await revokeUserSessions(userId, 'Account locked by admin');
  }

  return { user, lockedUntil };
}
