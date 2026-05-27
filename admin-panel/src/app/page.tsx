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

  useEffect(() => {
    Promise.all([
      apiFetch('/sessions').catch(() => []),
      apiFetch('/devices').catch(() => ({ devices: [] })),
      apiFetch('/cards').catch(() => ({ data: [] })),
      apiFetch('/logs').catch(() => ({ data: [] }))
    ]).then(([sessions, devices, cards, logs]) =>
      setStats({
        activeSessions: Array.isArray(sessions) ? sessions.length : (sessions?.data?.length || sessions?.sessions?.length || 0),
        devicesOnline: ((devices?.devices || devices?.data || []) as Array<{ online?: boolean }>).filter((d) => d.online).length,
        totalCards: cards?.data?.length || 0,
        logsToday: logs?.data?.length || 0
      })
    );
  }, []);

  const items = [
    ['Active Sessions', stats.activeSessions],
    ['Devices Online', stats.devicesOnline],
    ['Total Cards', stats.totalCards],
    ['Logs Today', stats.logsToday]
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
