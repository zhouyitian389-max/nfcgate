import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import { api, getToken } from '@/lib/api';

export default function CardsPage() {
  const router = useRouter();
  const [cards, setCards] = useState<any[]>([]);
  const [generated, setGenerated] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!getToken()) {
      router.push('/login');
      return;
    }
    loadCards();
  }, [router]);

  const loadCards = async () => {
    const res = await api.getCards();
    setCards(res.data || []);
  };

  const createToken = async (cardId: string) => {
    const res = await api.generateRelayToken(cardId);
    setGenerated((prev) => ({ ...prev, [cardId]: res.token }));
    loadCards();
  };

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-4">卡片列表</h1>
      <table className="w-full bg-white rounded shadow">
        <thead className="bg-gray-50">
          <tr>
            <th className="p-3 text-left">UID</th>
            <th className="p-3 text-left">Label</th>
            <th className="p-3 text-left">Type</th>
            <th className="p-3 text-left">Relay Token</th>
            <th className="p-3 text-left">Action</th>
          </tr>
        </thead>
        <tbody>
          {cards.map((c) => (
            <tr key={c.id} className="border-t">
              <td className="p-3 font-mono text-sm">{c.uid}</td>
              <td className="p-3">{c.label || '-'}</td>
              <td className="p-3">{c.type || '-'}</td>
              <td className="p-3 font-mono text-xs">{generated[c.id] || c.relayToken || '-'}</td>
              <td className="p-3">
                <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={() => createToken(c.id)}>
                  生成 Token
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Layout>
  );
}
