import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import { api, getToken } from '@/lib/api';

export default function RelayPage() {
  const router = useRouter();
  const [sessions, setSessions] = useState<any[]>([]);

  useEffect(() => {
    if (!getToken()) {
      router.push('/login');
      return;
    }

    const load = () => api.getRelaySessions().then((res) => setSessions(res.data || []));
    load();
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
  }, [router]);

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-4">活跃 Session</h1>
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
          {sessions.map((s) => (
            <tr key={s.sessionId} className="border-t">
              <td className="p-3 font-mono text-xs">{s.sessionId}</td>
              <td className="p-3 font-mono text-xs">{s.tokenPreview || 'hidden'}</td>
              <td className="p-3">{s.apduCount}</td>
              <td className="p-3 text-xs">hce:{String(s.connected?.hce)} reader:{String(s.connected?.reader)} external:{String(s.connected?.external)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Layout>
  );
}
