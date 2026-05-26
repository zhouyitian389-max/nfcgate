import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import Pagination from '@/components/Pagination';
import { api, createEventsSource, getToken } from '@/lib/api';

export default function LogsPage() {
  const router = useRouter();
  const [logs, setLogs] = useState<any[]>([]);
  const [error, setError] = useState('');
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const loadLogs = useCallback(async () => {
    try {
      setError('');
      const res = await api.getLogs();
      setLogs(res.data || []);
    } catch (err: any) {
      setError(err?.response?.data?.message || err.message || 'Failed to load logs');
    }
  }, []);

  useEffect(() => {
    if (!getToken()) {
      router.push('/login');
      return;
    }
    void loadLogs();
    const events = createEventsSource();
    const refresh = () => void loadLogs();
    events?.addEventListener('logs_changed', refresh);
    events?.addEventListener('relay_session_end', refresh);
    return () => events?.close();
  }, [loadLogs, router]);

  useEffect(() => {
    setPage(1);
  }, [logs.length]);

  const totalPages = Math.max(1, Math.ceil(logs.length / pageSize));
  const visibleLogs = useMemo(
    () => logs.slice((page - 1) * pageSize, page * pageSize),
    [logs, page]
  );

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-4">APDU 日志</h1>
      {error && <div className="mb-4 rounded bg-red-50 p-3 text-sm text-red-600">{error}</div>}
      <table className="w-full bg-white rounded shadow">
        <thead className="bg-gray-50">
          <tr>
            <th className="p-3 text-left">Session</th>
            <th className="p-3 text-left">Mode</th>
            <th className="p-3 text-left">APDU Count</th>
            <th className="p-3 text-left">Duration (ms)</th>
            <th className="p-3 text-left">Created</th>
          </tr>
        </thead>
        <tbody>
          {visibleLogs.map((l) => (
            <tr key={l.id} className="border-t">
              <td className="p-3 font-mono text-xs">{l.sessionId}</td>
              <td className="p-3">{l.mode}</td>
              <td className="p-3">{l.apduCount}</td>
              <td className="p-3">{l.duration}</td>
              <td className="p-3">{new Date(l.createdAt).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
    </Layout>
  );
}
