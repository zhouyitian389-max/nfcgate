import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import { apiFetch } from '@/lib/api';

export default function Dashboard() {
  const router = useRouter();
  const [stats, setStats] = useState<any>(null);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) { router.push('/login'); return; }
    apiFetch('/health').then(setStats).catch(() => {});
  }, []);

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-6">Dashboard</h1>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-gray-500 text-sm">Status</h3>
          <p className="text-2xl font-bold text-green-600">{stats?.status || '...'}</p>
        </div>
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-gray-500 text-sm">Server Time</h3>
          <p className="text-lg">{stats?.timestamp ? new Date(stats.timestamp).toLocaleString() : '...'}</p>
        </div>
      </div>
    </Layout>
  );
}
