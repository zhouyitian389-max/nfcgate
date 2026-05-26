const blacklist = new Map<string, number>();

export function addToBlacklist(token: string, expiresAt: number): void {
  blacklist.set(token, expiresAt);
}

export function isBlacklisted(token: string): boolean {
  const expiry = blacklist.get(token);
  if (expiry === undefined) return false;
  if (Date.now() > expiry) {
    blacklist.delete(token);
    return false;
  }
  return true;
}

setInterval(() => {
  const now = Date.now();
  for (const [token, expiry] of blacklist.entries()) {
    if (now > expiry) blacklist.delete(token);
  }
}, 5 * 60 * 1000).unref();
