'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

type DeviceRow = {
  id: string;
  deviceId: string;
  name?: string | null;
  type?: string | null;
  model?: string | null;
  osVersion?: string | null;
  lastSeen?: string | null;
  online?: boolean;
  account?: { id: string; name: string };
};

function csvEscape(value: unknown) {
  const text = String(value ?? '');
  return `"${text.replace(/"/g, '""')}"`;
}

export default function DevicesPage() {
  const [rows, setRows] = useState<DeviceRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const res = await apiFetch('/admin/devices');
      setRows(res.data || []);
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const deleteDevice = async (id: string) => {
    if (!window.confirm('Delete this device?')) return;
    await apiFetch(`/admin/devices/${id}`, { method: 'DELETE' });
    setMessage('Device deleted.');
    await load();
  };

  const exportDevices = () => {
    const header = ['deviceId', 'name', 'type', 'model', 'osVersion', 'lastSeen', 'status', 'account'];
    const body = rows.map((device) => [
      device.deviceId || '',
      device.name || '',
      device.type || '',
      device.model || '',
      device.osVersion || '',
      device.lastSeen ? new Date(device.lastSeen).toISOString() : '',
      device.online ? 'Online' : 'Offline',
      device.account?.name || ''
    ]);
    const csv = [header, ...body].map((line) => line.map(csvEscape).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'admin-devices.csv';
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold">Devices</h2>
          <p className="text-sm text-gray-500">Global device inventory across all accounts.</p>
        </div>
        <button className="rounded border px-3 py-2 bg-white" onClick={exportDevices}>Export CSV</button>
      </div>
      {message ? <div className="rounded border border-green-200 bg-green-50 p-3 text-sm text-green-800">{message}</div> : null}
      <div className="bg-white border rounded overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="p-2 text-left">Device ID</th>
              <th className="p-2 text-left">Name</th>
              <th className="p-2 text-left">Type</th>
              <th className="p-2 text-left">Model</th>
              <th className="p-2 text-left">OS</th>
              <th className="p-2 text-left">Last Seen</th>
              <th className="p-2 text-left">Status</th>
              <th className="p-2 text-left">Actions</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((device) => (
              <tr key={device.id} className="border-t">
                <td className="p-2">{device.deviceId}</td>
                <td className="p-2">{device.name || '-'}</td>
                <td className="p-2">{device.type || '-'}</td>
                <td className="p-2">{device.model || '-'}</td>
                <td className="p-2">{device.osVersion || '-'}</td>
                <td className="p-2">{device.lastSeen ? new Date(device.lastSeen).toLocaleString() : '-'}</td>
                <td className="p-2">{device.online ? 'Online' : 'Offline'}</td>
                <td className="p-2">
                  <button className="rounded border px-2 py-1 text-red-700" onClick={() => void deleteDevice(device.id)}>Delete</button>
                </td>
              </tr>
            ))}
            {!loading && rows.length === 0 ? (
              <tr><td className="p-4 text-gray-500" colSpan={8}>No devices found.</td></tr>
            ) : null}
            {loading ? (
              <tr><td className="p-4 text-gray-500" colSpan={8}>Loading devices...</td></tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </div>
  );
}
