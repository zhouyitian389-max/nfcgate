'use client';

import { FormEvent, useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

type LogRow = {
  id: string;
  accountId: string;
  sessionId: string;
  mode: string;
  apduCount: number;
  duration: number;
  createdAt: string;
  account?: { id: string; name: string };
};

function downloadJson(filename: string, payload: unknown) {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export default function LogsPage() {
  const [rows, setRows] = useState<LogRow[]>([]);
  const [sessionId, setSessionId] = useState('');
  const [mode, setMode] = useState('');
  const [error, setError] = useState('');

  const load = async (nextSessionId = '', nextMode = '') => {
    const params = new URLSearchParams({ limit: '200' });
    if (nextSessionId.trim()) params.set('sessionId', nextSessionId.trim());
    if (nextMode.trim()) params.set('mode', nextMode.trim());

    try {
      const data = await apiFetch(`/admin/logs?${params.toString()}`);
      setRows(data.data || []);
      setError('');
    } catch (err) {
      setRows([]);
      setError(err instanceof Error ? err.message : 'Failed to load logs');
    }
  };

  useEffect(() => {
    load().catch(() => undefined);
  }, []);

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    load(sessionId, mode).catch(() => undefined);
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-xl font-semibold">APDU Logs</h2>
        <button className="border rounded px-3 py-2" onClick={() => downloadJson('apdu-logs-export.json', rows)}>
          Export JSON
        </button>
      </div>
      <form onSubmit={onSubmit} className="flex flex-wrap gap-2">
        <input
          className="border rounded p-2"
          value={sessionId}
          onChange={(event) => setSessionId(event.target.value)}
          placeholder="Filter by sessionId"
        />
        <input
          className="border rounded p-2"
          value={mode}
          onChange={(event) => setMode(event.target.value)}
          placeholder="Filter by mode"
        />
        <button className="border rounded px-3">Filter</button>
      </form>
      {error ? <p className="text-sm text-red-600">{error}</p> : null}
      <div className="bg-white border rounded overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="p-2 text-left">Session</th>
              <th className="p-2 text-left">Account</th>
              <th className="p-2 text-left">Mode</th>
              <th className="p-2 text-left">APDU</th>
              <th className="p-2 text-left">Duration</th>
              <th className="p-2 text-left">Created</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td className="p-4 text-center text-gray-500" colSpan={6}>
                  No logs found
                </td>
              </tr>
            ) : null}
            {rows.map((log) => (
              <tr key={log.id} className="border-t">
                <td className="p-2">{log.sessionId}</td>
                <td className="p-2">{log.account?.name || log.accountId}</td>
                <td className="p-2">{log.mode}</td>
                <td className="p-2">{log.apduCount}</td>
                <td className="p-2">{log.duration}ms</td>
                <td className="p-2">{new Date(log.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
