import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import { apiFetch } from '@/lib/api';

export default function LogsPage() {
  const router = useRouter();
  const [logs, setLogs] = useState<any[]>([]);
  const [total, setTotal] = useState(0);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) { router.push('/login'); return; }
    loadLogs();
  }, []);

  const loadLogs = async () => {
    const res = await apiFetch('/admin/logs');
    setLogs(res.data);
    setTotal(res.total);
  };

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-4">操作日志 ({total})</h1>
      <table className="w-full bg-white rounded shadow">
        <thead className="bg-gray-50">
          <tr>
            <th className="p-3 text-left">Action</th>
            <th className="p-3 text-left">User</th>
            <th className="p-3 text-left">Detail</th>
            <th className="p-3 text-left">Time</th>
          </tr>
        </thead>
        <tbody>
          {logs.map((l) => (
            <tr key={l.id} className="border-t">
              <td className="p-3"><span className="px-2 py-1 bg-blue-100 text-blue-800 rounded text-xs">{l.action}</span></td>
              <td className="p-3 text-sm">{l.account?.email}</td>
              <td className="p-3 text-sm text-gray-600 max-w-xs truncate">{l.detail || '-'}</td>
              <td className="p-3 text-sm text-gray-500">{new Date(l.createdAt).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Layout>
  );
}
