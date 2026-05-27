'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

function csvEscape(value: unknown) {
  const text = String(value ?? '');
  return `"${text.replace(/"/g, '""')}"`;
}

export default function CardsPage() {
  const [rows, setRows] = useState<any[]>([]);
  const [message, setMessage] = useState('');

  const load = async () => {
    const res = await apiFetch('/admin/cards');
    setRows(res.data || []);
  };

  useEffect(() => {
    load().catch(() => setRows([]));
  }, []);

  const deleteCard = async (id: string) => {
    if (!window.confirm('Delete this card record?')) return;
    await apiFetch(`/admin/cards/${id}`, { method: 'DELETE' });
    setMessage('Card deleted.');
    await load();
  };

  const exportCards = () => {
    const header = ['id', 'account', 'uid', 'atr', 'label', 'type', 'createdAt'];
    const body = rows.map((card) => [
      card.id,
      card.account?.name || '',
      card.uid || '',
      card.atr || '',
      card.label || '',
      card.type || '',
      card.createdAt ? new Date(card.createdAt).toISOString() : ''
    ]);
    const csv = [header, ...body].map((line) => line.map(csvEscape).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'admin-cards.csv';
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold">Cards</h2>
          <p className="text-sm text-gray-500">All captured card records across accounts.</p>
        </div>
        <button className="rounded border px-3 py-2 bg-white" onClick={exportCards}>Export</button>
      </div>
      {message ? <div className="rounded border border-green-200 bg-green-50 p-3 text-sm text-green-800">{message}</div> : null}
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
            {rows.map((card) => (
              <tr key={card.id} className="border-t">
                <td className="p-2">{card.uid || '-'}</td>
                <td className="p-2">{card.atr || card.ats || '-'}</td>
                <td className="p-2">{card.label || '-'}</td>
                <td className="p-2">{card.account?.name || '-'}</td>
                <td className="p-2">{card.createdAt ? new Date(card.createdAt).toLocaleString() : '-'}</td>
                <td className="p-2">
                  <button className="rounded border px-2 py-1 text-red-700" onClick={() => deleteCard(card.id)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
