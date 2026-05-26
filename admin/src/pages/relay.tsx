import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import Pagination from '@/components/Pagination';
import { api, createEventsSource, getToken } from '@/lib/api';

export default function RelayPage() {
  const router = useRouter();
  const [sessions, setSessions] = useState<any[]>([]);
  const [error, setError] = useState('');
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const loadSessions = useCallback(async () => {
    try {
      setError('');
      const res = await api.getRelaySessions();
      setSessions(res.data || []);
    } catch (err: any) {
      setError(err?.response?.data?.message || err.message || 'Failed to load relay sessions');
    }
  }, []);

  useEffect(() => {
    if (!getToken()) {
      router.push('/login');
      return;
    }

    void loadSessions();
    const timer = setInterval(() => void loadSessions(), 5000);
    const events = createEventsSource();
    const refresh = () => void loadSessions();
    events?.addEventListener('relay_session_start', refresh);
    events?.addEventListener('relay_session_end', refresh);
    return () => {
      clearInterval(timer);
      events?.close();
    };
  }, [loadSessions, router]);

  useEffect(() => {
    setPage(1);
  }, [sessions.length]);

  const totalPages = Math.max(1, Math.ceil(sessions.length / pageSize));
  const visibleSessions = useMemo(
    () => sessions.slice((page - 1) * pageSize, page * pageSize),
    [page, sessions]
  );

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-4">活跃 Session</h1>
      {error && <div className="mb-4 rounded bg-red-50 p-3 text-sm text-red-600">{error}</div>}
      <table className="w-full bg-white rounded shadow">
        <thead className="bg-gray-50">
          <tr>
            <th className="p-3 text-left">Session ID</th>
            <th className="p-3 text-left">Token</th>
            <th className="p-3 text-left">APDU</th>
            <th className="p-3 text-left">Connected</th>
          </tr>
        </thead>
        <tbody>
          {visibleSessions.map((s) => (
            <tr key={s.sessionId} className="border-t">
              <td className="p-3 font-mono text-xs">{s.sessionId}</td>
              <td className="p-3 font-mono text-xs">{maskToken(s.token)}</td>
              <td className="p-3">{s.apduCount}</td>
              <td className="p-3 text-xs">hce:{String(s.connected?.hce)} reader:{String(s.connected?.reader)} external:{String(s.connected?.external)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
    </Layout>
  );
}

function maskToken(token?: string | null) {
  if (!token) return '-';
  if (token.length <= 12) return token;
  return `${token.slice(0, 8)}…${token.slice(-4)}`;
}
