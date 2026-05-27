'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

type CardRow = {
  id: string;
  uid?: string | null;
  atr?: string | null;
  label?: string | null;
  createdAt: string;
  account?: { id: string; name: string };
};

function downloadJson(filename: string, payload: unknown) {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export default function CardsPage() {
  const [rows, setRows] = useState<CardRow[]>([]);
  const [error, setError] = useState('');

  const load = async () => {
    try {
      const res = await apiFetch('/admin/cards?limit=200');
      setRows(res.data || []);
      setError('');
    } catch (err) {
      setRows([]);
      setError(err instanceof Error ? err.message : 'Failed to load cards');
    }
  };

  useEffect(() => {
    load().catch(() => undefined);
  }, []);

  const onDelete = async (id: string) => {
    if (!window.confirm('Delete this stored card?')) return;
    await apiFetch(`/admin/cards/${id}`, { method: 'DELETE' });
    await load();
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-xl font-semibold">Cards</h2>
        <button className="border rounded px-3 py-2" onClick={() => downloadJson('cards-export.json', rows)}>
          Export JSON
        </button>
      </div>
      {error ? <p className="text-sm text-red-600">{error}</p> : null}
      <div className="bg-white border rounded overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="p-2 text-left">UID</th>
              <th className="p-2 text-left">ATR</th>
              <th className="p-2 text-left">Label</th>
              <th className="p-2 text-left">Account</th>
              <th className="p-2 text-left">Created</th>
              <th className="p-2 text-left">Actions</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td className="p-4 text-center text-gray-500" colSpan={6}>
                  No cards found
                </td>
              </tr>
            ) : null}
            {rows.map((card) => (
              <tr key={card.id} className="border-t">
                <td className="p-2">{card.uid || '-'}</td>
                <td className="p-2">{card.atr || '-'}</td>
                <td className="p-2">{card.label || '-'}</td>
                <td className="p-2">{card.account?.name || '-'}</td>
                <td className="p-2">{new Date(card.createdAt).toLocaleString()}</td>
                <td className="p-2">
                  <button className="border rounded px-2 py-1 text-red-600" onClick={() => onDelete(card.id)}>
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
