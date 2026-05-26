'use client';

import { useRouter } from 'next/navigation';
import { apiFetch, clearAuthToken } from '@/lib/api';

export default function LogoutButton() {
  const router = useRouter();

  const onLogout = async () => {
    try {
      await apiFetch('/auth/logout', { method: 'POST', body: JSON.stringify({}) });
    } catch {
      // ignore logout transport failures; local cleanup still applies
    }
    clearAuthToken();
    router.push('/login');
  };

  return (
    <button className="text-sm text-red-600" onClick={onLogout} type="button">
      Logout
    </button>
  );
}
