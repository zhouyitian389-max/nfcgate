'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

export default function CardsPage() {
  const [rows, setRows] = useState<any[]>([]);
  useEffect(() => {
    apiFetch('/cards').then((res) => setRows(res.data || [])).catch(() => setRows([]));
  }, []);

  return (
    <div className="bg-white border rounded overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="bg-gray-50">
          <tr><th className="p-2 text-left">UID</th><th className="p-2 text-left">ATR</th><th className="p-2 text-left">Label</th></tr>
        </thead>
        <tbody>
          {rows.map((card) => (
            <tr key={card.id} className="border-t"><td className="p-2">{card.uid}</td><td className="p-2">{card.atr || card.ats || '-'}</td><td className="p-2">{card.label || '-'}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
