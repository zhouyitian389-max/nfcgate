import type { ReactNode } from 'react';
import AuthProvider from '@/components/AuthProvider';
import Sidebar from '@/components/Sidebar';

export default function DashboardLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen">
      <Sidebar />
      <main className="flex-1 p-6 pt-14 md:pt-6">
        <AuthProvider>{children}</AuthProvider>
      </main>
    </div>
  );
}
