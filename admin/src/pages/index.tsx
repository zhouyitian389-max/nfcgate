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
    apiFetch('/stats').then(setStats).catch(() => {});
  }, []);

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-6">Dashboard</h1>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-gray-500 text-sm">用户数</h3>
          <p className="text-3xl font-bold text-blue-600">{stats?.users ?? '...'}</p>
        </div>
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-gray-500 text-sm">卡片数</h3>
          <p className="text-3xl font-bold text-green-600">{stats?.cards ?? '...'}</p>
        </div>
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-gray-500 text-sm">设备数</h3>
          <p className="text-3xl font-bold text-purple-600">{stats?.devices ?? '...'}</p>
        </div>
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-gray-500 text-sm">活跃中继</h3>
          <p className="text-3xl font-bold text-orange-600">{stats?.activeSessions ?? '...'}</p>
        </div>
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-gray-500 text-sm">总中继次数</h3>
          <p className="text-3xl font-bold text-gray-700">{stats?.sessions ?? '...'}</p>
        </div>
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-gray-500 text-sm">操作日志</h3>
          <p className="text-3xl font-bold text-gray-700">{stats?.logs ?? '...'}</p>
        </div>
      </div>
    </Layout>
  );
}
