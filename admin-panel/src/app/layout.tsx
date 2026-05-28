import './globals.css';
import type { ReactNode } from 'react';
import LogoutButton from '@/components/LogoutButton';
import AuthProvider from '@/components/AuthProvider';
import NavLink from '@/components/NavLink';

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-gray-100 text-gray-900">
        <div className="flex min-h-screen">
          <aside className="w-56 bg-white border-r p-4 space-y-2">
            <h1 className="text-lg font-semibold mb-4">NFC Relay Admin</h1>
            <nav className="flex flex-col gap-2 text-sm">
              <NavLink href="/" label="Dashboard" />
              <NavLink href="/sessions" label="Sessions" />
              <NavLink href="/users" label="Users" />
              <NavLink href="/devices" label="Devices" />
              <NavLink href="/cards" label="Cards" />
              <NavLink href="/logs" label="Logs" />
            </nav>
            <div className="pt-4">
              <LogoutButton />
            </div>
          </aside>
          <main className="flex-1 p-6">
            <AuthProvider>{children}</AuthProvider>
          </main>
        </div>
      </body>
    </html>
  );
}
