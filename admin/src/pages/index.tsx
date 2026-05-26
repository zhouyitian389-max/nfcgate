import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import { api, createEventsSource, getToken } from '@/lib/api';

export default function Dashboard() {
  const router = useRouter();
  const [stats, setStats] = useState<any>(null);
  const [error, setError] = useState('');

  const loadStats = useCallback(async () => {
    try {
      setError('');
      setStats(await api.getStats());
    } catch (err: any) {
      setError(err?.response?.data?.message || err.message || 'Failed to load dashboard');
    }
  }, []);

  useEffect(() => {
    if (!getToken()) {
      router.push('/login');
      return;
    }
    void loadStats();

    const events = createEventsSource();
    const refresh = () => void loadStats();
    events?.addEventListener('cards_changed', refresh);
    events?.addEventListener('devices_changed', refresh);
    events?.addEventListener('logs_changed', refresh);
    events?.addEventListener('relay_session_start', refresh);
    events?.addEventListener('relay_session_end', refresh);
    return () => events?.close();
  }, [loadStats, router]);

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-6">Dashboard</h1>
      {error && <div className="mb-4 rounded bg-red-50 p-3 text-sm text-red-600">{error}</div>}
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
