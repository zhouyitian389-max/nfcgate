const HEX_RE = /^[0-9a-fA-F]+$/;
const TRACK2_RE = /^[0-9D=F]+$/;
const EMAIL_RE = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$/;
const PASSWORD_RE = /^(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()\-_=+[\]{};:'",.<>/?\\|`~]).{8,256}$/;

export interface CardPayload {
  uid?: unknown;
  atr?: unknown;
  atR?: unknown;
  atqa?: unknown;
  sak?: unknown;
  aidList?: unknown;
  label?: unknown;
  track2?: unknown;
  type?: unknown;
  ats?: unknown;
  historicalBytes?: unknown;
}

export interface ValidationResult<T> {
  ok: boolean;
  errors: string[];
  value?: T;
}

export function normalizeEmail(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const normalized = value.trim().toLowerCase();
  return normalized || undefined;
}

export function isValidEmail(email: string): boolean {
  return EMAIL_RE.test(email);
}

export function isStrongPassword(password: unknown): password is string {
  return typeof password === 'string' && PASSWORD_RE.test(password);
}

export function normalizeTrack2(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const normalized = value.trim().toUpperCase();
  if (!normalized || normalized.length > 100 || !TRACK2_RE.test(normalized)) {
    return undefined;
  }
  return normalized;
}

function optionalHex(value: unknown, field: string, minLen: number, maxLen: number, errors: string[]) {
  if (value == null) return undefined;
  if (typeof value !== 'string') {
    errors.push(`${field} must be a string`);
    return undefined;
  }
  const normalized = value.trim();
  if (normalized.length < minLen || normalized.length > maxLen || !HEX_RE.test(normalized)) {
    errors.push(`${field} must be hex with length ${minLen}-${maxLen}`);
    return undefined;
  }
  return normalized.toUpperCase();
}

export function validateCardPayload(input: CardPayload, isPatch = false): ValidationResult<{
  uid?: string;
  atr?: string;
  atqa?: string;
  sak?: string;
  aidList?: string[];
  label?: string;
  track2?: string;
  type?: string;
  ats?: string;
  historicalBytes?: string;
}> {
  const errors: string[] = [];
  const uid = optionalHex(input.uid, 'uid', 8, 40, errors);
  if (!isPatch && !uid) {
    errors.push('uid is required');
  }

  const atr = optionalHex(input.atr ?? input.atR, 'atr', 2, 200, errors);
  const atqa = optionalHex(input.atqa, 'atqa', 2, 8, errors);
  const sak = optionalHex(input.sak, 'sak', 2, 2, errors);
  const ats = optionalHex(input.ats, 'ats', 2, 200, errors);
  const historicalBytes = optionalHex(input.historicalBytes, 'historicalBytes', 2, 200, errors);

  let aidList: string[] | undefined;
  if (input.aidList != null) {
    if (!Array.isArray(input.aidList)) {
      errors.push('aidList must be an array');
    } else {
      aidList = [];
      for (const aid of input.aidList) {
        if (typeof aid !== 'string') {
          errors.push('aidList items must be strings');
          continue;
        }
        const normalized = aid.trim().toUpperCase();
        if (normalized.length < 7 || normalized.length > 40 || !HEX_RE.test(normalized)) {
          errors.push('aidList items must be ISO AID-like hex with length 7-40');
          continue;
        }
        aidList.push(normalized);
      }
    }
  }

  let label: string | undefined;
  if (input.label != null) {
    if (typeof input.label !== 'string') {
      errors.push('label must be a string');
    } else if (input.label.length > 100) {
      errors.push('label must be <= 100 characters');
    } else {
      label = input.label.trim();
    }
  }

  let track2: string | undefined;
  if (input.track2 != null) {
    if (typeof input.track2 !== 'string') {
      errors.push('track2 must be a string');
    } else {
      const normalized = normalizeTrack2(input.track2);
      if (!normalized) {
        errors.push('track2 must be <= 100 chars and only contain digits with D/= separators');
      } else {
        track2 = normalized;
      }
    }
  }

  let type: string | undefined;
  if (input.type != null) {
    if (typeof input.type !== 'string') {
      errors.push('type must be a string');
    } else {
      type = input.type;
    }
  }

  if (errors.length > 0) {
    return { ok: false, errors };
  }

  return {
    ok: true,
    errors: [],
    value: { uid, atr, atqa, sak, ats, historicalBytes, aidList, label, track2, type }
  };
}
