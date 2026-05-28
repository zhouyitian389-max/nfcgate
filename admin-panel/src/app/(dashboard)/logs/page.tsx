'use client';

import { FormEvent, useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

function csvEscape(value: unknown) {
  const text = String(value ?? '');
  return `"${text.replace(/"/g, '""')}"`;
}

export default function LogsPage() {
  const [rows, setRows] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [sessionId, setSessionId] = useState('');
  const [mode, setMode] = useState('');
  const [message, setMessage] = useState('');
  const limit = 20;
  const pageCount = Math.max(1, Math.ceil(total / limit));

  const load = async (filterSessionId = '', filterMode = '', targetPage = page) => {
    setLoading(true);
    const params = new URLSearchParams();
    if (filterSessionId) params.set('sessionId', filterSessionId);
    if (filterMode) params.set('mode', filterMode);
    params.set('page', String(targetPage));
    params.set('limit', String(limit));
    const query = params.size ? `?${params.toString()}` : '';
    const data = await apiFetch(`/admin/logs${query}`);
    setRows(data.data || []);
    setTotal(Number(data.total) || 0);
    setPage(Number(data.page) || targetPage);
    setLoading(false);
  };

  useEffect(() => {
    load(sessionId, mode, page).catch(() => {
      setRows([]);
      setTotal(0);
      setLoading(false);
    });
  }, [page]);

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setPage(1);
    load(sessionId, mode, 1).catch(() => {
      setRows([]);
      setTotal(0);
      setLoading(false);
    });
  };

  const deleteLog = async (id: string) => {
    if (!window.confirm('Delete this APDU log entry?')) return;
    await apiFetch(`/admin/logs/${id}`, { method: 'DELETE' });
    setMessage('Log deleted.');
    await load(sessionId, mode);
  };

  const exportLogs = () => {
    const header = ['sessionId', 'account', 'mode', 'apduCount', 'duration', 'createdAt', 'closedReason'];
    const body = rows.map((log) => [
      log.sessionId,
      log.account?.name || '',
      log.mode || '',
      log.apduCount ?? 0,
      log.duration ?? 0,
      log.createdAt ? new Date(log.createdAt).toISOString() : '',
      log.closedReason || ''
    ]);
    const csv = [header, ...body].map((line) => line.map(csvEscape).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'admin-logs.csv';
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold">Logs</h2>
          <p className="text-sm text-gray-500">APDU relay logs across all accounts.</p>
        </div>
        <button className="border rounded px-3 py-2 bg-white" onClick={exportLogs}>Export</button>
      </div>
      {message ? <div className="rounded border border-green-200 bg-green-50 p-3 text-sm text-green-800">{message}</div> : null}
      {loading ? <div className="text-sm text-gray-500">加载中...</div> : null}
      <form onSubmit={onSubmit} className="flex gap-2 flex-wrap">
        <input className="border rounded p-2" value={sessionId} onChange={(e) => setSessionId(e.target.value)} placeholder="Filter by sessionId" />
        <select className="border rounded p-2" value={mode} onChange={(e) => setMode(e.target.value)}>
          <option value="">All modes</option>
          <option value="NFC_RELAY">NFC_RELAY</option>
          <option value="EMV_EXTERNAL">EMV_EXTERNAL</option>
        </select>
        <button className="border rounded px-3">Filter</button>
      </form>
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
              <th className="p-2 text-left">Actions</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((log) => (
              <tr key={log.id} className="border-t">
                <td className="p-2">{log.sessionId}</td>
                <td className="p-2">{log.account?.name || '-'}</td>
                <td className="p-2">{log.mode}</td>
                <td className="p-2">{log.apduCount}</td>
                <td className="p-2">{log.duration} ms</td>
                <td className="p-2">{log.createdAt ? new Date(log.createdAt).toLocaleString() : '-'}</td>
                <td className="p-2">
                  <button className="rounded border px-2 py-1 text-red-700" onClick={() => deleteLog(log.id)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="flex items-center justify-between text-sm text-gray-600">
        <span>共 {total} 条，每页 {limit} 条，第 {page} / {pageCount} 页</span>
        <div className="flex gap-2">
          <button
            className="rounded border bg-white px-3 py-1 disabled:opacity-50"
            onClick={() => setPage((prev) => Math.max(1, prev - 1))}
            disabled={loading || page <= 1}
            type="button"
          >
            上一页
          </button>
          <button
            className="rounded border bg-white px-3 py-1 disabled:opacity-50"
            onClick={() => setPage((prev) => Math.min(pageCount, prev + 1))}
            disabled={loading || page >= pageCount}
            type="button"
          >
            下一页
          </button>
        </div>
      </div>
    </div>
  );
}
