import { tokenStorage } from './tokenStorage';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080/api';

/**
 * Perform a clean logout:
 *  1. Call the server logout endpoint to revoke the session.
 *  2. Clear all locally stored tokens.
 *  3. Redirect to the login page with an optional message.
 */
export async function logout(reason?: string): Promise<void> {
  const token = tokenStorage.getAccessToken();

  try {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    await fetch(`${API_BASE}/auth/logout`, {
      method: 'POST',
      headers,
      body: JSON.stringify({})
    });
  } catch {
    // Transport failures should not block local cleanup.
  }

  tokenStorage.clearTokens();

  if (typeof window !== 'undefined') {
    const params = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    window.location.href = `/login${params}`;
  }
}
