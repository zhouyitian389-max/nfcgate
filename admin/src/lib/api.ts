import axios from 'axios';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const TOKEN_KEY = 'token';

export const getToken = () => (typeof window === 'undefined' ? null : localStorage.getItem(TOKEN_KEY));
export const setToken = (token: string) => localStorage.setItem(TOKEN_KEY, token);
export const clearToken = () => localStorage.removeItem(TOKEN_KEY);

const client = axios.create({
  baseURL: API_BASE,
  timeout: 15_000
});

client.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401 && typeof window !== 'undefined') {
      clearToken();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const api = {
  login: async (email: string, password: string) => (await client.post('/auth/login', { email, password })).data,
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
