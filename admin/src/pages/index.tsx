import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import { api, getToken } from '@/lib/api';

export default function Dashboard() {
  const router = useRouter();
  const [stats, setStats] = useState<any>({ devices: 0, sessions: 0, apduCount: 0, cards: 0, activeSessions: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!getToken()) {
      router.push('/login');
      return;
    }
    api.getStats()
      .then(setStats)
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, [router]);

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-6">Dashboard</h1>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card title="设备数" value={stats.devices} loading={loading} />
        <Card title="Session 数" value={stats.sessions} loading={loading} />
        <Card title="APDU 总量" value={stats.apduCount} loading={loading} />
        <Card title="卡片数" value={stats.cards} loading={loading} />
        <Card title="活跃中继" value={stats.activeSessions} loading={loading} />
      </div>
    </Layout>
  );
}

function Card({ title, value, loading }: { title: string; value: number; loading: boolean }) {
  return (
    <div className="bg-white p-6 rounded-lg shadow">
      <h3 className="text-gray-500 text-sm">{title}</h3>
      <p className="text-3xl font-bold text-gray-900">{loading ? '...' : value}</p>
    </div>
  );
}
