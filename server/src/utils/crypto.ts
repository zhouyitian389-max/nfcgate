import crypto from 'crypto';

const DEFAULT_CARD_KEY = crypto.createHash('sha256').update('nfcgate-dev-card-key').digest();
const KEY_LENGTH = 32;
let cardKeyCache: Buffer | null = null;

function resolveCardKey(): Buffer {
  const raw = process.env.CARD_ENCRYPTION_KEY?.trim();
  if (!raw) {
    if (process.env.NODE_ENV === 'production') {
      throw new Error('CARD_ENCRYPTION_KEY must be set in production');
    }
    return DEFAULT_CARD_KEY;
  }

  if (/^[0-9a-fA-F]{64}$/.test(raw)) {
    return Buffer.from(raw, 'hex');
  }

  if (process.env.NODE_ENV === 'production') {
    throw new Error('CARD_ENCRYPTION_KEY must be a 64-character hex string in production');
  }

  return crypto.createHash('sha256').update(raw).digest();
}

function getCardKey(): Buffer {
  if (cardKeyCache) return cardKeyCache;
  const key = resolveCardKey();
  if (key.length !== KEY_LENGTH) {
    throw new Error('CARD_ENCRYPTION_KEY must resolve to 32 bytes');
  }
  cardKeyCache = key;
  return cardKeyCache;
}

export function encryptString(value: string): string {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', getCardKey(), iv);
  const ciphertext = Buffer.concat([cipher.update(value, 'utf8'), cipher.final()]);
  const authTag = cipher.getAuthTag();
  return `v1:${iv.toString('base64')}:${authTag.toString('base64')}:${ciphertext.toString('base64')}`;
}

export function decryptString(payload: string): string {
  const parts = payload.split(':');
  if (parts.length !== 4 || parts[0] !== 'v1') {
    throw new Error('Invalid encrypted payload');
  }

  const [, ivPart, authTagPart, ciphertextPart] = parts;
  const decipher = crypto.createDecipheriv('aes-256-gcm', getCardKey(), Buffer.from(ivPart, 'base64'));
  decipher.setAuthTag(Buffer.from(authTagPart, 'base64'));
  const plaintext = Buffer.concat([
    decipher.update(Buffer.from(ciphertextPart, 'base64')),
    decipher.final()
  ]);
  return plaintext.toString('utf8');
}
