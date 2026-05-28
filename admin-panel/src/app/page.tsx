'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

type Stats = {
  activeSessions: number;
  devices: number;
  totalCards: number;
  totalLogs: number;
};

export default function DashboardPage() {
  const [stats, setStats] = useState<Stats>({ activeSessions: 0, devices: 0, totalCards: 0, totalLogs: 0 });

  useEffect(() => {
    Promise.all([
      apiFetch('/admin/stats').catch(() => apiFetch('/stats').catch(() => ({}))),
      apiFetch('/sessions').catch(() => ({ data: [] as Array<unknown> }))
    ]).then(([summary, sessions]) => {
      const activeSessions = Array.isArray(sessions)
        ? sessions.length
        : (Array.isArray(sessions?.data) ? sessions.data.length : 0);
      setStats({
        activeSessions,
        devices: Number(summary?.devices) || 0,
        totalCards: Number(summary?.cards) || 0,
        totalLogs: Number(summary?.logs) || 0
      });
    });
  }, []);

  const items = [
    ['Active Sessions', stats.activeSessions],
    ['Devices', stats.devices],
    ['Total Cards', stats.totalCards],
    ['Total Logs', stats.totalLogs]
  ];

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      {items.map(([label, value]) => (
        <div key={label} className="rounded bg-white border p-4">
          <p className="text-sm text-gray-500">{label}</p>
          <p className="text-3xl font-semibold">{value}</p>
        </div>
      ))}
    </div>
  );
}
