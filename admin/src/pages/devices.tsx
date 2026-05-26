import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import Pagination from '@/components/Pagination';
import { api, createEventsSource, getToken } from '@/lib/api';

export default function DevicesPage() {
  const router = useRouter();
  const [devices, setDevices] = useState<any[]>([]);
  const [error, setError] = useState('');
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const loadDevices = useCallback(async () => {
    try {
      setError('');
      const res = await api.getDevices();
      setDevices(res.data || []);
    } catch (err: any) {
      setError(err?.response?.data?.message || err.message || 'Failed to load devices');
    }
  }, []);

  useEffect(() => {
    if (!getToken()) {
      router.push('/login');
      return;
    }
    void loadDevices();
    const events = createEventsSource();
    const refresh = () => void loadDevices();
    events?.addEventListener('devices_changed', refresh);
    return () => events?.close();
  }, [loadDevices, router]);

  useEffect(() => {
    setPage(1);
  }, [devices.length]);

  const totalPages = Math.max(1, Math.ceil(devices.length / pageSize));
  const visibleDevices = useMemo(
    () => devices.slice((page - 1) * pageSize, page * pageSize),
    [devices, page]
  );

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-4">设备列表</h1>
      {error && <div className="mb-4 rounded bg-red-50 p-3 text-sm text-red-600">{error}</div>}
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
          {visibleDevices.map((d) => (
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
      <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
    </Layout>
  );
}
