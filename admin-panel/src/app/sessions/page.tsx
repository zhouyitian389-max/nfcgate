'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

export default function SessionsPage() {
  const [rows, setRows] = useState<any[]>([]);
  useEffect(() => {
    const load = () => apiFetch('/sessions').then((res) => setRows(res.data || [])).catch(() => setRows([]));
    load();
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="bg-white border rounded overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="bg-gray-50"><tr><th className="p-2 text-left">Session ID</th><th className="p-2 text-left">Mode</th><th className="p-2 text-left">APDU</th></tr></thead>
        <tbody>
          {rows.map((session) => (
            <tr key={session.sessionId} className="border-t"><td className="p-2">{session.sessionId}</td><td className="p-2">{session.mode}</td><td className="p-2">{session.apduCount}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
