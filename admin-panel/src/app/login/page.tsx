'use client';

import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { setAuthToken, setRefreshToken } from '@/lib/api';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080/api';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const router = useRouter();

  const resolveErrorMessage = (status: number) => {
    if (status === 401) return '认证失败，请检查邮箱和密码。';
    if (status === 423) return '账户已被锁定，请稍后再试。';
    if (status === 429) return '请求过于频繁，请稍后再试。';
    if (status >= 500) return '服务器发生错误，请稍后重试。';
    return '登录失败';
  };

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });
    if (!res.ok) {
      setError(resolveErrorMessage(res.status));
      return;
    }
    const payload = await res.json() as { accessToken?: string; token?: string; refreshToken?: string };
    setAuthToken(payload.accessToken || payload.token || '');
    if (payload.refreshToken) setRefreshToken(payload.refreshToken);
    router.push('/');
  };

  return (
    <form onSubmit={onSubmit} className="max-w-sm bg-white border rounded p-6 space-y-3">
      <h2 className="text-xl font-semibold">Login</h2>
      <input className="w-full border p-2 rounded" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Email" required />
      <input className="w-full border p-2 rounded" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Password" required />
      {error ? <p className="text-sm text-red-600">{error}</p> : null}
      <button className="w-full bg-black text-white rounded p-2" type="submit">Sign in</button>
    </form>
  );
}
