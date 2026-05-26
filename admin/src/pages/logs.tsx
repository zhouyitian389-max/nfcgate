import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import { api, getToken } from '@/lib/api';

export default function LogsPage() {
  const router = useRouter();
  const [logs, setLogs] = useState<any[]>([]);

  useEffect(() => {
    if (!getToken()) {
      router.push('/login');
      return;
    }
    api.getLogs().then((res) => setLogs(res.data || [])).catch(() => undefined);
  }, [router]);

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-4">APDU 日志</h1>
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
          {logs.map((l) => (
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
    </Layout>
  );
}
