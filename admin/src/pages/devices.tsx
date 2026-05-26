import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import { apiFetch } from '@/lib/api';

export default function DevicesPage() {
  const router = useRouter();
  const [devices, setDevices] = useState<any[]>([]);
  const [total, setTotal] = useState(0);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) { router.push('/login'); return; }
    loadDevices();
  }, []);

  const loadDevices = async () => {
    const res = await apiFetch('/admin/devices');
    setDevices(res.data);
    setTotal(res.total);
  };

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-4">设备管理 ({total})</h1>
      <table className="w-full bg-white rounded shadow">
        <thead className="bg-gray-50">
          <tr>
            <th className="p-3 text-left">Name</th>
            <th className="p-3 text-left">Platform</th>
            <th className="p-3 text-left">Owner</th>
            <th className="p-3 text-left">Last Seen</th>
            <th className="p-3 text-left">Created</th>
          </tr>
        </thead>
        <tbody>
          {devices.map((d) => (
            <tr key={d.id} className="border-t">
              <td className="p-3 font-medium">{d.name}</td>
              <td className="p-3 text-sm">{d.platform}</td>
              <td className="p-3 text-sm text-gray-600">{d.account?.email}</td>
              <td className="p-3 text-sm text-gray-500">{new Date(d.lastSeen).toLocaleString()}</td>
              <td className="p-3 text-sm text-gray-500">{new Date(d.createdAt).toLocaleDateString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Layout>
  );
}
