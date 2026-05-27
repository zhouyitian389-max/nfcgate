import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

function tokenExpired(jwt: string): boolean {
  try {
    const parts = jwt.split('.');
    if (parts.length !== 3) return true;
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const payload = JSON.parse(atob(padded)) as { exp?: number };
    if (!payload.exp || !Number.isFinite(payload.exp)) return true;
    return Date.now() >= payload.exp * 1000;
  } catch {
    return true;
  }
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  if (pathname.startsWith('/login') || pathname.startsWith('/_next')) {
    return NextResponse.next();
  }
  const jwtCookie = request.cookies.get('jwt');
  if (!jwtCookie || tokenExpired(jwtCookie.value)) {
    return NextResponse.redirect(new URL('/login', request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)']
};
