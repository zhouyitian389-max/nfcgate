import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import { apiFetch } from '@/lib/api';

export default function CardsPage() {
  const router = useRouter();
  const [cards, setCards] = useState<any[]>([]);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) { router.push('/login'); return; }
    loadCards();
  }, []);

  const loadCards = async () => {
    const res = await apiFetch('/admin/cards');
    setCards(res.data);
  };

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-4">卡片审计</h1>
      <table className="w-full bg-white rounded shadow">
        <thead className="bg-gray-50">
          <tr>
            <th className="p-3 text-left">UID</th>
            <th className="p-3 text-left">Type</th>
            <th className="p-3 text-left">Label</th>
            <th className="p-3 text-left">Owner</th>
            <th className="p-3 text-left">Created</th>
          </tr>
        </thead>
        <tbody>
          {cards.map((c) => (
            <tr key={c.id} className="border-t">
              <td className="p-3 font-mono text-sm">{c.uid}</td>
              <td className="p-3 text-sm">{c.type || '-'}</td>
              <td className="p-3">{c.label || '-'}</td>
              <td className="p-3 text-sm text-gray-600">{c.account?.email}</td>
              <td className="p-3 text-sm text-gray-500">{new Date(c.createdAt).toLocaleDateString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Layout>
  );
}
