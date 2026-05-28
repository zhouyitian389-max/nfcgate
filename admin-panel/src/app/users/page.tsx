'use client';

import { FormEvent, useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

type UserRow = {
  id: string;
  email: string;
  name?: string | null;
  role: string;
  createdAt: string;
  status?: string;
  lockedUntil?: string | null;
  account?: { id: string; name: string };
};

function csvEscape(value: unknown) {
  const text = String(value ?? '');
  return `"${text.replace(/"/g, '""')}"`;
}

export default function UsersPage() {
  const [rows, setRows] = useState<UserRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [form, setForm] = useState({ email: '', password: '', name: '' });
  const limit = 20;
  const pageCount = Math.max(1, Math.ceil(total / limit));

  const load = async (targetPage = page) => {
    setLoading(true);
    setError('');
    try {
      const res = await apiFetch(`/admin/users?page=${targetPage}&limit=${limit}`);
      setRows(res.data || []);
      setTotal(Number(res.total) || 0);
      setPage(Number(res.page) || targetPage);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load users');
      setRows([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(page).catch(() => undefined);
  }, [page]);

  const createUser = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    setMessage('');
    try {
      await apiFetch('/admin/users', {
        method: 'POST',
        body: JSON.stringify(form)
      });
      setForm({ email: '', password: '', name: '' });
      setShowCreate(false);
      setMessage('User created successfully.');
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create user');
    }
  };

  const deleteUser = async (user: UserRow) => {
    if (!window.confirm(`Delete ${user.email}?`)) return;
    await apiFetch(`/admin/users/${user.id}`, { method: 'DELETE' });
    await load();
  };

  const toggleLock = async (user: UserRow) => {
    await apiFetch(`/admin/users/${user.id}/toggle-lock`, { method: 'PUT' });
    await load();
  };

  const resetPassword = async (user: UserRow) => {
    const password = window.prompt(`Enter a new password for ${user.email}.\nLeave blank to auto-generate.`, '') || '';
    const payload = await apiFetch(`/admin/users/${user.id}/reset-password`, {
      method: 'PUT',
      body: JSON.stringify(password ? { password } : {})
    });
    const nextPassword = payload?.password ? ` Temporary password: ${payload.password}` : '';
    setMessage(`Password reset for ${user.email}.${nextPassword}`);
    await load();
  };

  const exportUsers = () => {
    const header = ['email', 'name', 'role', 'account', 'createdAt', 'status'];
    const body = rows.map((row) => [
      row.email,
      row.name || '',
      row.role,
      row.account?.name || '',
      new Date(row.createdAt).toISOString(),
      row.status || 'active'
    ]);
    const csv = [header, ...body].map((line) => line.map(csvEscape).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'admin-users.csv';
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold">Users</h2>
          <p className="text-sm text-gray-500">Admin-only account management for app logins.</p>
        </div>
        <div className="flex gap-2">
          <button className="rounded border px-3 py-2 bg-white" onClick={exportUsers}>Export</button>
          <button className="rounded bg-black px-3 py-2 text-white" onClick={() => setShowCreate(true)}>Create user</button>
        </div>
      </div>

      {message ? <div className="rounded border border-green-200 bg-green-50 p-3 text-sm text-green-800">{message}</div> : null}
      {error ? <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div> : null}

      {showCreate ? (
        <div className="rounded border bg-white p-4 shadow-sm">
          <form className="grid gap-3 md:grid-cols-2" onSubmit={createUser}>
            <input className="rounded border p-2" placeholder="Email" type="email" value={form.email} onChange={(e) => setForm((prev) => ({ ...prev, email: e.target.value }))} required />
            <input className="rounded border p-2" placeholder="Password" type="password" value={form.password} onChange={(e) => setForm((prev) => ({ ...prev, password: e.target.value }))} required />
            <input className="rounded border p-2 md:col-span-2" placeholder="Name" value={form.name} onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))} />
            <div className="md:col-span-2 flex gap-2 justify-end">
              <button className="rounded border px-3 py-2" type="button" onClick={() => setShowCreate(false)}>Cancel</button>
              <button className="rounded bg-black px-3 py-2 text-white" type="submit">Save</button>
            </div>
          </form>
        </div>
      ) : null}

      <div className="overflow-x-auto rounded border bg-white">
        <table className="w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="p-2 text-left">Email</th>
              <th className="p-2 text-left">Name</th>
              <th className="p-2 text-left">Role</th>
              <th className="p-2 text-left">Created</th>
              <th className="p-2 text-left">Status</th>
              <th className="p-2 text-left">Actions</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((user) => {
              const locked = user.status === 'locked' || Boolean(user.lockedUntil && new Date(user.lockedUntil).getTime() > Date.now());
              return (
                <tr key={user.id} className="border-t align-top">
                  <td className="p-2">{user.email}</td>
                  <td className="p-2">{user.name || '-'}</td>
                  <td className="p-2">{user.role}</td>
                  <td className="p-2">{new Date(user.createdAt).toLocaleString()}</td>
                  <td className="p-2">{locked ? 'Locked' : 'Active'}</td>
                  <td className="p-2">
                    <div className="flex flex-wrap gap-2">
                      <button className="rounded border px-2 py-1" onClick={() => resetPassword(user)}>Reset password</button>
                      <button className="rounded border px-2 py-1" onClick={() => toggleLock(user)}>{locked ? 'Unlock' : 'Lock'}</button>
                      <button className="rounded border px-2 py-1 text-red-700" onClick={() => deleteUser(user)}>Delete</button>
                    </div>
                  </td>
                </tr>
              );
            })}
            {!loading && rows.length === 0 ? (
              <tr><td className="p-4 text-gray-500" colSpan={6}>No users found.</td></tr>
            ) : null}
          </tbody>
        </table>
      </div>
      <div className="flex items-center justify-between text-sm text-gray-600">
        <span>共 {total} 条，每页 {limit} 条，第 {page} / {pageCount} 页</span>
        <div className="flex gap-2">
          <button
            className="rounded border bg-white px-3 py-1 disabled:opacity-50"
            onClick={() => setPage((prev) => Math.max(1, prev - 1))}
            disabled={loading || page <= 1}
          >
            上一页
          </button>
          <button
            className="rounded border bg-white px-3 py-1 disabled:opacity-50"
            onClick={() => setPage((prev) => Math.min(pageCount, prev + 1))}
            disabled={loading || page >= pageCount}
          >
            下一页
          </button>
        </div>
      </div>
    </div>
  );
}
