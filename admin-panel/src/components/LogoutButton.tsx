'use client';

import { logout } from '@/lib/logoutHandler';

export default function LogoutButton() {
  const onLogout = async () => {
    await logout();
  };

  return (
    <button className="text-sm text-red-600" onClick={onLogout} type="button">
      Logout
    </button>
  );
}
