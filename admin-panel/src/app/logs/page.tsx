'use client';

import { FormEvent, useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

export default function LogsPage() {
  const [rows, setRows] = useState<any[]>([]);
  const [sessionId, setSessionId] = useState('');

  const load = async (filterSessionId = '') => {
    const query = filterSessionId ? `?sessionId=${encodeURIComponent(filterSessionId)}` : '';
    const data = await apiFetch(`/logs${query}`);
    setRows(data.data || []);
  };

  useEffect(() => {
    load().catch(() => setRows([]));
  }, []);

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    load(sessionId).catch(() => setRows([]));
  };

  return (
    <div className="space-y-3">
      <form onSubmit={onSubmit} className="flex gap-2">
        <input className="border rounded p-2" value={sessionId} onChange={(e) => setSessionId(e.target.value)} placeholder="Filter by sessionId" />
        <button className="border rounded px-3">Filter</button>
      </form>
      <div className="bg-white border rounded overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50"><tr><th className="p-2 text-left">Session</th><th className="p-2 text-left">Mode</th><th className="p-2 text-left">APDU</th></tr></thead>
          <tbody>
            {rows.map((log) => (
              <tr key={log.id} className="border-t"><td className="p-2">{log.sessionId}</td><td className="p-2">{log.mode}</td><td className="p-2">{log.apduCount}</td></tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
