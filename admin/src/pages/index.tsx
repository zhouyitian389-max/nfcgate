import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import { api, getToken } from '@/lib/api';

export default function Dashboard() {
  const router = useRouter();
  const [stats, setStats] = useState<any>(null);

  useEffect(() => {
    if (!getToken()) {
      router.push('/login');
      return;
    }
    api.getStats().then(setStats).catch(() => undefined);
  }, [router]);

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-6">Dashboard</h1>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card title="设备数" value={stats?.devices} />
        <Card title="Session 数" value={stats?.sessions} />
        <Card title="APDU 总量" value={stats?.apduCount} />
        <Card title="卡片数" value={stats?.cards} />
        <Card title="活跃中继" value={stats?.activeSessions} />
      </div>
    </Layout>
  );
}

function Card({ title, value }: { title: string; value?: number }) {
  return (
    <div className="bg-white p-6 rounded-lg shadow">
      <h3 className="text-gray-500 text-sm">{title}</h3>
      <p className="text-3xl font-bold text-gray-900">{value ?? '...'}</p>
    </div>
  );
}
