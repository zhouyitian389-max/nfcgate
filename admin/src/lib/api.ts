import axios from 'axios';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const TOKEN_KEY = 'token';
const REFRESH_TOKEN_KEY = 'refresh_token';

function readCookie(name: string) {
  if (typeof document === 'undefined') return null;
  const prefix = `${name}=`;
  const match = document.cookie.split('; ').find((item) => item.startsWith(prefix));
  return match ? decodeURIComponent(match.slice(prefix.length)) : null;
}

function writeCookie(name: string, value: string, maxAgeSeconds: number) {
  if (typeof document === 'undefined') return;
  const secure = window.location.protocol === 'https:' ? '; Secure' : '';
  document.cookie = `${name}=${encodeURIComponent(value)}; Path=/; SameSite=Strict${secure}; Max-Age=${maxAgeSeconds}`;
}

function clearCookie(name: string) {
  if (typeof document === 'undefined') return;
  const secure = window.location.protocol === 'https:' ? '; Secure' : '';
  document.cookie = `${name}=; Path=/; SameSite=Strict${secure}; Max-Age=0`;
}

export const getToken = () => readCookie(TOKEN_KEY);
export const getRefreshToken = () => readCookie(REFRESH_TOKEN_KEY);
export const setToken = (token: string) => writeCookie(TOKEN_KEY, token, 7 * 24 * 60 * 60);
export const setTokens = (token: string, refreshToken?: string) => {
  setToken(token);
  if (refreshToken) {
    writeCookie(REFRESH_TOKEN_KEY, refreshToken, 30 * 24 * 60 * 60);
  }
};
export const clearToken = () => {
  clearCookie(TOKEN_KEY);
  clearCookie(REFRESH_TOKEN_KEY);
};

const client = axios.create({
  baseURL: API_BASE,
  timeout: 15_000
});

client.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = 'Bearer' + ' ' + token;
  }
  return config;
});

let refreshPromise: Promise<string | null> | null = null;

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error?.response?.status === 401 && typeof window !== 'undefined') {
      const originalRequest = error.config as { _retry?: boolean; headers?: Record<string, string | undefined> };
      const refreshToken = getRefreshToken();
      if (!refreshToken || originalRequest?._retry) {
        clearToken();
        window.location.href = '/login';
        return Promise.reject(error);
      }

      originalRequest._retry = true;
      if (!refreshPromise) {
        refreshPromise = client
          .post('/auth/refresh', { refreshToken }, { headers: { Authorization: undefined } })
          .then((res) => {
            const newToken = res.data?.token as string | undefined;
            const newRefreshToken = res.data?.refreshToken as string | undefined;
            if (!newToken) {
              throw new Error('Missing token in refresh response');
            }
            setTokens(newToken, newRefreshToken);
            return newToken;
          })
          .catch(() => {
            clearToken();
            window.location.href = '/login';
            return null;
          })
          .finally(() => {
            refreshPromise = null;
          });
      }

      const nextToken = await refreshPromise;
      if (nextToken && originalRequest?.headers) {
        originalRequest.headers.Authorization = 'Bearer' + ' ' + nextToken;
        return client.request(originalRequest);
      }
    }
    return Promise.reject(error);
  }
);

export const api = {
  login: async (email: string, password: string) => (await client.post('/auth/login', { email, password })).data,
  logout: async () => (await client.post('/auth/logout', {})).data,
  me: async () => (await client.get('/auth/me')).data,
  getStats: async () => (await client.get('/stats')).data,
  getCards: async () => (await client.get('/cards')).data,
  generateRelayToken: async (cardId: string) => (await client.post(`/cards/${cardId}/relay-token`)).data,
  getDevices: async () => (await client.get('/devices')).data,
  getRelaySessions: async () => (await client.get('/relay/sessions')).data,
  getLogs: async () => (await client.get('/logs')).data,
  getLog: async (id: string) => (await client.get(`/logs/${id}`)).data,
  getUsers: async () => (await client.get('/admin/users')).data,
  getAdminStats: async () => (await client.get('/admin/stats')).data
};
