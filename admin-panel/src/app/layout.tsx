import './globals.css';
import Link from 'next/link';
import type { ReactNode } from 'react';
import LogoutButton from '@/components/LogoutButton';

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-gray-100 text-gray-900">
        <div className="flex min-h-screen">
          <aside className="w-56 bg-white border-r p-4 space-y-2">
            <h1 className="text-lg font-semibold mb-4">NFC Relay Admin</h1>
            <nav className="flex flex-col gap-2 text-sm">
              <Link href="/">Dashboard</Link>
              <Link href="/sessions">Sessions</Link>
              <Link href="/devices">Devices</Link>
              <Link href="/cards">Cards</Link>
              <Link href="/logs">Logs</Link>
            </nav>
            <div className="pt-4">
              <LogoutButton />
            </div>
          </aside>
          <main className="flex-1 p-6">{children}</main>
        </div>
      </body>
    </html>
  );
}
