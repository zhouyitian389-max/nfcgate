import Link from 'next/link';
import { useRouter } from 'next/router';
import { ReactNode } from 'react';
import { api, clearToken } from '@/lib/api';

interface LayoutProps {
  children: ReactNode;
}

const navItems = [
  { href: '/', label: '📊 Dashboard', icon: '📊' },
  { href: '/cards', label: '💳 Cards', icon: '💳' },
  { href: '/devices', label: '📱 Devices', icon: '📱' },
  { href: '/relay', label: '🔄 Relay Sessions', icon: '🔄' },
  { href: '/logs', label: '📋 APDU Logs', icon: '📋' },
  { href: '/users', label: '👥 Users', icon: '👥' },
];

export default function Layout({ children }: LayoutProps) {
  const router = useRouter();

  const handleLogout = async () => {
    try {
      await api.logout();
    } catch {
      // ignore logout transport failures; local cleanup still applies
    }
    clearToken();
    router.push('/login');
  };

  return (
    <div className="flex h-screen">
      {/* Sidebar */}
      <aside className="w-64 bg-gray-900 text-white flex flex-col">
        <div className="p-4 border-b border-gray-700">
          <h1 className="text-xl font-bold">🔐 NFCGate</h1>
          <p className="text-xs text-gray-400 mt-1">Admin Panel v2</p>
        </div>

        <nav className="flex-1 p-4 space-y-1">
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={`block px-3 py-2 rounded-md text-sm transition-colors ${
                router.pathname === item.href
                  ? 'bg-blue-600 text-white'
                  : 'text-gray-300 hover:bg-gray-800 hover:text-white'
              }`}
            >
              {item.label}
            </Link>
          ))}
        </nav>

        <div className="p-4 border-t border-gray-700">
          <button
            onClick={handleLogout}
            className="w-full px-3 py-2 text-sm text-red-400 hover:bg-gray-800 rounded-md transition-colors"
          >
            🚪 Logout
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto p-6">
        {children}
      </main>
    </div>
  );
}
