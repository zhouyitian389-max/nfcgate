import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

function decodeBase64Url(value: string): string {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  if (typeof Buffer !== 'undefined') {
    return Buffer.from(padded, 'base64').toString('utf8');
  }
  const binary = atob(padded);
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

function tokenExpired(jwt: string): boolean {
  try {
    const parts = jwt.split('.');
    if (parts.length !== 3) return true;
    const payload = JSON.parse(decodeBase64Url(parts[1])) as { exp?: number };
    if (!payload.exp || !Number.isFinite(payload.exp)) return true;
    return Date.now() >= payload.exp * 1000;
  } catch {
    return true;
  }
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  if (
    pathname.startsWith('/api')
    || pathname.startsWith('/_next')
    || pathname.startsWith('/login')
    || pathname === '/favicon.ico'
    || /\.[^/]+$/.test(pathname)
  ) {
    return NextResponse.next();
  }
  const jwtCookie = request.cookies.get('jwt');
  if (!jwtCookie || tokenExpired(jwtCookie.value)) {
    return NextResponse.redirect(new URL('/login', request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico|login(?:/|$)|.*\\..*).*)']
};
