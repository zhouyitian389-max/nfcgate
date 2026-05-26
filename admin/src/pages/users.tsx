import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import Pagination from '@/components/Pagination';
import { api, getToken } from '@/lib/api';

export default function UsersPage() {
  const router = useRouter();
  const [users, setUsers] = useState<any[]>([]);
  const [error, setError] = useState('');
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const loadUsers = useCallback(async () => {
    try {
      setError('');
      const res = await api.getUsers();
      setUsers(res.data || []);
    } catch (err: any) {
      setError(err?.response?.data?.message || err.message || 'Failed to load users');
    }
  }, []);

  useEffect(() => {
    if (!getToken()) {
      router.push('/login');
      return;
    }
    void loadUsers();
  }, [loadUsers, router]);

  useEffect(() => {
    setPage(1);
  }, [users.length]);

  const totalPages = Math.max(1, Math.ceil(users.length / pageSize));
  const visibleUsers = useMemo(
    () => users.slice((page - 1) * pageSize, page * pageSize),
    [page, users]
  );

  return (
    <Layout>
      <h1 className="text-2xl font-bold mb-4">用户管理</h1>
      {error && <div className="mb-4 rounded bg-red-50 p-3 text-sm text-red-600">{error}</div>}
      <table className="w-full bg-white rounded shadow">
        <thead className="bg-gray-50">
          <tr>
            <th className="p-3 text-left">Email</th>
            <th className="p-3 text-left">Name</th>
            <th className="p-3 text-left">Role</th>
            <th className="p-3 text-left">Account</th>
            <th className="p-3 text-left">Created</th>
          </tr>
        </thead>
        <tbody>
          {visibleUsers.map((u) => (
            <tr key={u.id} className="border-t">
              <td className="p-3">{u.email}</td>
              <td className="p-3">{u.name || '-'}</td>
              <td className="p-3">{u.role}</td>
              <td className="p-3">{u.account?.name || '-'}</td>
              <td className="p-3">{new Date(u.createdAt).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
    </Layout>
  );
}
