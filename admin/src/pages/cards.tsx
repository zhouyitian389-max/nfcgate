import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import Pagination from '@/components/Pagination';
import { api, createEventsSource, getToken } from '@/lib/api';

export default function CardsPage() {
  const router = useRouter();
  const [cards, setCards] = useState<any[]>([]);
  const [generated, setGenerated] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const loadCards = useCallback(async () => {
    try {
      setError('');
      const res = await api.getCards();
      setCards(res.data || []);
    } catch (err: any) {
      setError(err?.response?.data?.message || err.message || 'Failed to load cards');
    }
  }, []);

  useEffect(() => {
    if (!getToken()) {
      router.push('/login');
      return;
    }
    void loadCards();
    const events = createEventsSource();
    const refresh = () => void loadCards();
    events?.addEventListener('cards_changed', refresh);
    return () => events?.close();
  }, [loadCards, router]);

  useEffect(() => {
    setPage(1);
  }, [cards.length]);

  const createToken = async (cardId: string) => {
    try {
      setError('');
      const res = await api.generateRelayToken(cardId);
      setGenerated((prev) => ({ ...prev, [cardId]: res.token }));
      await loadCards();
    } catch (err: any) {
      setError(err?.response?.data?.message || err.message || 'Failed to generate relay token');
    }
  };

  const totalPages = Math.max(1, Math.ceil(cards.length / pageSize));
  const visibleCards = useMemo(
    () => cards.slice((page - 1) * pageSize, page * pageSize),
    [cards, page]
  );

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-4">卡片列表</h1>
      {error && <div className="mb-4 rounded bg-red-50 p-3 text-sm text-red-600">{error}</div>}
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
          {visibleCards.map((c) => (
            <tr key={c.id} className="border-t">
              <td className="p-3 font-mono text-sm">{c.uid}</td>
              <td className="p-3">{c.label || '-'}</td>
              <td className="p-3">{c.type || '-'}</td>
              <td className="p-3 font-mono text-xs">{maskToken(generated[c.id] || c.relayToken)}</td>
              <td className="p-3">
                <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={() => createToken(c.id)}>
                  生成 Token
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
    </Layout>
  );
}

function maskToken(token?: string | null) {
  if (!token) return '-';
  if (token.length <= 12) return token;
  return `${token.slice(0, 8)}…${token.slice(-4)}`;
}
