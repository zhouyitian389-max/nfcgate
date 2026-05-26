'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

export default function DevicesPage() {
  const [rows, setRows] = useState<any[]>([]);
  useEffect(() => {
    apiFetch('/devices').then((res) => setRows(res.data || [])).catch(() => setRows([]));
  }, []);

  return (
    <div className="bg-white border rounded overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="bg-gray-50">
          <tr><th className="p-2 text-left">Device ID</th><th className="p-2 text-left">Name</th><th className="p-2 text-left">Status</th></tr>
        </thead>
        <tbody>
          {rows.map((device) => (
            <tr key={device.id} className="border-t"><td className="p-2">{device.deviceId}</td><td className="p-2">{device.name || '-'}</td><td className="p-2">{device.online ? 'Online' : 'Offline'}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
