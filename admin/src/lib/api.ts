const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('token');
}

export function setToken(token: string) {
  localStorage.setItem('token', token);
}

export function clearToken() {
  localStorage.removeItem('token');
}

export async function apiFetch<T = any>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {}),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  if (res.status === 401) {
    clearToken();
    if (typeof window !== 'undefined') {
      window.location.href = '/login';
    }
    throw new Error('Unauthorized');
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || `HTTP ${res.status}`);
  }

  return res.json();
}

// Auth
export const api = {
  login: (email: string, password: string) =>
    apiFetch('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),

  me: () => apiFetch('/auth/me'),

  // Users (admin)
  getUsers: (page = 1) => apiFetch(`/admin/users?page=${page}`),
  createUser: (data: any) =>
    apiFetch('/admin/users', { method: 'POST', body: JSON.stringify(data) }),
  deleteUser: (id: string) =>
    apiFetch(`/admin/users/${id}`, { method: 'DELETE' }),

  // Cards
  getCards: (page = 1) => apiFetch(`/admin/cards?page=${page}`),

  // Logs
  getLogs: (page = 1) => apiFetch(`/admin/logs?page=${page}`),

  // Stats
  getStats: () => apiFetch('/stats'),

  // Devices
  getDevices: () => apiFetch('/devices'),
};
