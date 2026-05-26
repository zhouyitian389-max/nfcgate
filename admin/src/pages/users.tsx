import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/Layout';
import { apiFetch } from '@/lib/api';

export default function UsersPage() {
  const router = useRouter();
  const [users, setUsers] = useState<any[]>([]);
  const [total, setTotal] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const [newEmail, setNewEmail] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newRole, setNewRole] = useState('USER');
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) { router.push('/login'); return; }
    loadUsers();
  }, []);

  const loadUsers = async () => {
    const res = await apiFetch('/admin/users');
    setUsers(res.data);
    setTotal(res.total);
  };

  const createUser = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    try {
      await apiFetch('/admin/users', {
        method: 'POST',
        body: JSON.stringify({ email: newEmail, password: newPassword, role: newRole }),
      });
      setNewEmail('');
      setNewPassword('');
      setNewRole('USER');
      setShowForm(false);
      await loadUsers();
    } catch (err: any) {
      alert(err.message);
    } finally {
      setCreating(false);
    }
  };

  const deleteUser = async (id: string) => {
    if (!confirm('确定删除该用户？')) return;
    try {
      await apiFetch(`/admin/users/${id}`, { method: 'DELETE' });
      await loadUsers();
    } catch (err: any) {
      alert(err.message);
    }
  };

  return (
    <Layout>
      <div className="flex justify-between items-center mb-4">
        <h1 className="text-2xl font-bold">用户管理 ({total})</h1>
        <button
          onClick={() => setShowForm(!showForm)}
          className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
        >
          {showForm ? '取消' : '+ 开设账户'}
        </button>
      </div>

      {showForm && (
        <form onSubmit={createUser} className="bg-white p-4 rounded shadow mb-4 flex gap-3 items-end">
          <div>
            <label className="block text-xs text-gray-500 mb-1">邮箱</label>
            <input
              type="email"
              value={newEmail}
              onChange={(e) => setNewEmail(e.target.value)}
              className="px-3 py-2 border rounded"
              required
            />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">密码</label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="px-3 py-2 border rounded"
              required
            />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">角色</label>
            <select
              value={newRole}
              onChange={(e) => setNewRole(e.target.value)}
              className="px-3 py-2 border rounded"
            >
              <option value="USER">USER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </div>
          <button
            type="submit"
            disabled={creating}
            className="px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700 disabled:opacity-50"
          >
            创建
          </button>
        </form>
      )}

      <table className="w-full bg-white rounded shadow">
        <thead className="bg-gray-50">
          <tr>
            <th className="p-3 text-left">Email</th>
            <th className="p-3 text-left">Role</th>
            <th className="p-3 text-left">Created</th>
            <th className="p-3 text-left">Actions</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id} className="border-t">
              <td className="p-3">{u.email}</td>
              <td className="p-3">
                <span className={`px-2 py-1 rounded text-xs ${u.role === 'ADMIN' ? 'bg-red-100 text-red-800' : 'bg-gray-100 text-gray-800'}`}>
                  {u.role}
                </span>
              </td>
              <td className="p-3 text-sm text-gray-500">{new Date(u.createdAt).toLocaleDateString()}</td>
              <td className="p-3">
                <button
                  onClick={() => deleteUser(u.id)}
                  className="text-red-600 hover:underline text-sm"
                >
                  删除
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Layout>
  );
}
