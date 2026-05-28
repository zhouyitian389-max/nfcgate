'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

type Stats = {
  activeSessions: number;
  devicesOnline: number;
  totalCards: number;
  logsToday: number;
};

export default function DashboardPage() {
  const [stats, setStats] = useState<Stats>({ activeSessions: 0, devicesOnline: 0, totalCards: 0, logsToday: 0 });
  const [loading, setLoading] = useState(true);

  const loadStats = async () => {
    try {
      const data = await apiFetch('/stats');
      setStats({
        activeSessions: data?.activeSessions || 0,
        devicesOnline: data?.today_active || 0,
        totalCards: (data?.cards || 0) + (data?.cloudCards || 0),
        logsToday: data?.sessions || 0
      });
    } catch {
      setStats({
        activeSessions: 0,
        devicesOnline: 0,
        totalCards: 0,
        logsToday: 0
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadStats();
    const timer = setInterval(() => {
      void loadStats();
    }, 30_000);
    return () => clearInterval(timer);
  }, []);

  const items = [
    ['Active Sessions', stats.activeSessions],
    ['Devices Online', stats.devicesOnline],
    ['Total Cards', stats.totalCards],
    ['Logs Today', stats.logsToday]
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold">Dashboard</h2>
        <button className="rounded border px-3 py-2 bg-white" onClick={() => void loadStats()}>
          Refresh
        </button>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {items.map(([label, value]) => (
          <div key={label} className="rounded bg-white border p-4">
            <p className="text-sm text-gray-500">{label}</p>
            <p className="text-3xl font-semibold">{loading ? '-' : value}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
