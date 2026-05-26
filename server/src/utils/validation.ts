import { HttpError } from './http.js';

type StringOptions = {
  minLength?: number;
  maxLength?: number;
  pattern?: RegExp;
};

export function requireObject(value: unknown, field = 'body'): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new HttpError(400, `${field} must be an object`);
  }
  return value as Record<string, unknown>;
}

export function requireString(value: unknown, field: string, options: StringOptions = {}): string {
  if (typeof value !== 'string') {
    throw new HttpError(400, `${field} must be a string`);
  }

  const normalized = value.trim();
  if (options.minLength && normalized.length < options.minLength) {
    throw new HttpError(400, `${field} must be at least ${options.minLength} characters`);
  }
  if (options.maxLength && normalized.length > options.maxLength) {
    throw new HttpError(400, `${field} must be at most ${options.maxLength} characters`);
  }
  if (options.pattern && !options.pattern.test(normalized)) {
    throw new HttpError(400, `${field} has an invalid format`);
  }

  return normalized;
}

export function optionalString(value: unknown, field: string, options: StringOptions = {}): string | undefined {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  return requireString(value, field, options);
}

export function requireHexString(value: unknown, field: string, maxLength: number): string {
  return requireString(value, field, {
    minLength: 2,
    maxLength,
    pattern: /^[0-9a-fA-F]+$/
  }).toUpperCase();
}

export function optionalHexString(value: unknown, field: string, maxLength: number): string | undefined {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  return requireHexString(value, field, maxLength);
}

export function requireEmail(value: unknown): string {
  return requireString(value, 'email', {
    maxLength: 254,
    pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  }).toLowerCase();
}

export function requireEnum<T extends string>(value: unknown, field: string, allowed: readonly T[]): T {
  const normalized = requireString(value, field, { maxLength: 64 });
  if (!allowed.includes(normalized as T)) {
    throw new HttpError(400, `${field} must be one of ${allowed.join(', ')}`);
  }
  return normalized as T;
}

export function optionalEnum<T extends string>(value: unknown, field: string, allowed: readonly T[]): T | undefined {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  return requireEnum(value, field, allowed);
}

export function optionalStringArray(value: unknown, field: string, itemMaxLength: number): string[] | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  if (!Array.isArray(value)) {
    throw new HttpError(400, `${field} must be an array`);
  }

  return value.map((item, index) =>
    requireString(item, `${field}[${index}]`, { maxLength: itemMaxLength })
  );
}

export function optionalHexArray(value: unknown, field: string, itemMaxLength: number): string[] | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  if (!Array.isArray(value)) {
    throw new HttpError(400, `${field} must be an array`);
  }

  return value.map((item, index) => requireHexString(item, `${field}[${index}]`, itemMaxLength));
}

export function parsePagination(value: unknown, fallback: number, max: number): number {
  if (value === undefined || value === null || value === '') {
    return fallback;
  }

  const parsed = Number.parseInt(String(value), 10);
  if (!Number.isFinite(parsed) || parsed < 1) {
    throw new HttpError(400, 'Invalid pagination value');
  }

  return Math.min(parsed, max);
}
