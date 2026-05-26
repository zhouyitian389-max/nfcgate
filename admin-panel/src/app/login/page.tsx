'use client';

import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080/api';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const router = useRouter();

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });
    if (!res.ok) {
      setError('Login failed');
      return;
    }
    const payload = await res.json();
    localStorage.setItem('jwt', payload.accessToken || payload.token);
    document.cookie = 'jwt=1; Path=/';
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
