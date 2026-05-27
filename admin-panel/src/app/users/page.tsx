'use client';

import { FormEvent, useEffect, useMemo, useState } from 'react';
import { apiFetch } from '@/lib/api';

type UserRow = {
  id: string;
  email: string;
  name?: string | null;
  role: string;
  createdAt: string;
  lockedUntil?: string | null;
  account?: { id: string; name: string };
};

type CreateForm = {
  email: string;
  password: string;
  name: string;
};

const initialForm: CreateForm = { email: '', password: '', name: '' };

export default function UsersPage() {
  const [rows, setRows] = useState<UserRow[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState<CreateForm>(initialForm);
  const [submitting, setSubmitting] = useState(false);

  const filteredRows = useMemo(() => rows, [rows]);

  const load = async (query = '') => {
    setLoading(true);
    setError('');
    try {
      const params = new URLSearchParams({ limit: '200' });
      if (query.trim()) params.set('search', query.trim());
      const data = await apiFetch(`/admin/users?${params.toString()}`);
      setRows(data.data || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load users');
      setRows([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load().catch(() => undefined);
  }, []);

  const onCreate = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      await apiFetch('/admin/users', {
        method: 'POST',
        body: JSON.stringify({
          email: form.email,
          password: form.password,
          name: form.name
        })
      });
      setForm(initialForm);
      setShowCreate(false);
      await load(search);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create user');
    } finally {
      setSubmitting(false);
    }
  };

  const onDelete = async (id: string) => {
    if (!window.confirm('Delete this user and all account data?')) return;
    await apiFetch(`/admin/users/${id}`, { method: 'DELETE' });
    await load(search);
  };

  const onResetPassword = async (id: string) => {
    const password = window.prompt('Enter the new password');
    if (!password) return;
    await apiFetch(`/admin/users/${id}/reset-password`, {
      method: 'PUT',
      body: JSON.stringify({ password })
    });
    window.alert('Password updated');
  };

  const onToggleLock = async (user: UserRow) => {
    await apiFetch(`/admin/users/${user.id}/toggle-lock`, {
      method: 'PUT',
      body: JSON.stringify({ locked: !user.lockedUntil || new Date(user.lockedUntil).getTime() <= Date.now() })
    });
    await load(search);
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-semibold">Users</h2>
        <div className="flex gap-2">
          <input
            className="border rounded px-3 py-2"
            placeholder="Search email or name"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <button className="border rounded px-3 py-2" onClick={() => load(search)}>
            Search
          </button>
          <button className="bg-black text-white rounded px-3 py-2" onClick={() => setShowCreate(true)}>
            Create user
          </button>
        </div>
      </div>

      {error ? <p className="text-sm text-red-600">{error}</p> : null}

      <div className="bg-white border rounded overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="p-2 text-left">Email</th>
              <th className="p-2 text-left">Name</th>
              <th className="p-2 text-left">Role</th>
              <th className="p-2 text-left">Created</th>
              <th className="p-2 text-left">Status</th>
              <th className="p-2 text-left">Account</th>
              <th className="p-2 text-left">Actions</th>
            </tr>
          </thead>
          <tbody>
            {!loading && filteredRows.length === 0 ? (
              <tr>
                <td className="p-4 text-center text-gray-500" colSpan={7}>
                  No users found
                </td>
              </tr>
            ) : null}
            {filteredRows.map((user) => {
              const locked = Boolean(user.lockedUntil && new Date(user.lockedUntil).getTime() > Date.now());
              return (
                <tr key={user.id} className="border-t">
                  <td className="p-2">{user.email}</td>
                  <td className="p-2">{user.name || '-'}</td>
                  <td className="p-2">{user.role}</td>
                  <td className="p-2">{new Date(user.createdAt).toLocaleString()}</td>
                  <td className="p-2">{locked ? 'Locked' : 'Active'}</td>
                  <td className="p-2">{user.account?.name || '-'}</td>
                  <td className="p-2">
                    <div className="flex flex-wrap gap-2">
                      <button className="border rounded px-2 py-1" onClick={() => onResetPassword(user.id)}>
                        Reset password
                      </button>
                      <button className="border rounded px-2 py-1" onClick={() => onToggleLock(user)}>
                        {locked ? 'Unlock' : 'Lock'}
                      </button>
                      <button className="border rounded px-2 py-1 text-red-600" onClick={() => onDelete(user.id)}>
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {showCreate ? (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4">
          <form className="w-full max-w-md bg-white rounded border p-6 space-y-3" onSubmit={onCreate}>
            <h3 className="text-lg font-semibold">Create user</h3>
            <input
              className="w-full border rounded px-3 py-2"
              type="email"
              placeholder="Email"
              value={form.email}
              onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
              required
            />
            <input
              className="w-full border rounded px-3 py-2"
              type="password"
              placeholder="Password"
              value={form.password}
              onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
              required
            />
            <input
              className="w-full border rounded px-3 py-2"
              placeholder="Name"
              value={form.name}
              onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
            />
            <div className="flex justify-end gap-2">
              <button className="border rounded px-3 py-2" type="button" onClick={() => setShowCreate(false)}>
                Cancel
              </button>
              <button className="bg-black text-white rounded px-3 py-2" type="submit" disabled={submitting}>
                {submitting ? 'Creating...' : 'Create'}
              </button>
            </div>
          </form>
        </div>
      ) : null}
    </div>
  );
}
