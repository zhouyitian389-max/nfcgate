import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import { api, getToken } from '@/lib/api';

export default function DevicesPage() {
  const router = useRouter();
  const [devices, setDevices] = useState<any[]>([]);

  useEffect(() => {
    if (!getToken()) {
      router.push('/login');
      return;
    }
    api.getDevices().then((res) => setDevices(res.data || [])).catch(() => undefined);
  }, [router]);

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-4">设备列表</h1>
      <table className="w-full bg-white rounded shadow">
        <thead className="bg-gray-50">
          <tr>
            <th className="p-3 text-left">Device ID</th>
            <th className="p-3 text-left">Name</th>
            <th className="p-3 text-left">Type</th>
            <th className="p-3 text-left">Online</th>
            <th className="p-3 text-left">Last Seen</th>
          </tr>
        </thead>
        <tbody>
          {devices.map((d) => (
            <tr key={d.id} className="border-t">
              <td className="p-3 font-mono text-xs">{d.deviceId}</td>
              <td className="p-3">{d.name || '-'}</td>
              <td className="p-3">{d.type}</td>
              <td className="p-3">{d.online ? 'Yes' : 'No'}</td>
              <td className="p-3">{new Date(d.lastSeen).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Layout>
  );
}
