'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiFetch, tokenStorage } from '@/lib/api';

type SessionRow = {
  sessionId: string;
  tokenPreview?: string;
  mode?: string;
  apduCount?: number;
  startedAt?: string;
  lastActivityAt?: string;
  connected?: {
    hce?: boolean;
    reader?: boolean;
    external?: boolean;
  };
};

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080/api';

function StatusDot({ online }: { online?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2">
      <span className={`inline-block h-2.5 w-2.5 rounded-full ${online ? 'bg-green-500' : 'bg-red-500'}`} />
      <span>{online ? '在线' : '离线'}</span>
    </span>
  );
}

function formatRelativeTime(value?: string) {
  if (!value) return '-';
  const ts = Date.parse(value);
  if (!Number.isFinite(ts)) return '-';
  const diffSec = Math.max(0, Math.floor((Date.now() - ts) / 1000));
  if (diffSec < 60) return `${diffSec}秒前`;
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}分钟前`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}小时前`;
  return `${Math.floor(diffHour / 24)}天前`;
}

export default function SessionsPage() {
  const [rows, setRows] = useState<SessionRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  const loadSessions = useCallback(async (showLoading = false) => {
    if (showLoading) {
      setLoading(true);
    }
    try {
      const res = await apiFetch('/sessions');
      setRows(res?.data || []);
      setMessage('');
    } catch {
      setRows([]);
      setMessage('加载会话失败');
    } finally {
      if (showLoading) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void loadSessions(true);
    const timer = setInterval(() => {
      void loadSessions();
    }, 3000);

    const controller = new AbortController();
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
    let stopped = false;
    const connectSse = async () => {
      if (stopped) return;
      const token = tokenStorage.getAccessToken();
      if (!token || tokenStorage.isAccessTokenExpired()) return;
      try {
        const response = await fetch(`${API_BASE}/events/stream`, {
          headers: { Authorization: 'Bearer ' + token },
          cache: 'no-store',
          signal: controller.signal
        });
        if (!response.ok || !response.body) {
          return;
        }
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          let boundary = buffer.indexOf('\n\n');
          while (boundary >= 0) {
            const eventBlock = buffer.slice(0, boundary);
            buffer = buffer.slice(boundary + 2);
            if (eventBlock.includes('event: relay_session_start') || eventBlock.includes('event: relay_session_end')) {
              void loadSessions();
            }
            boundary = buffer.indexOf('\n\n');
          }
        }
      } catch {
        // fallback to polling only
      } finally {
        if (!stopped && !controller.signal.aborted && !tokenStorage.isAccessTokenExpired()) {
          reconnectTimer = setTimeout(() => {
            void connectSse();
          }, 2000);
        }
      }
    };
    void connectSse();

    return () => {
      stopped = true;
      clearInterval(timer);
      if (reconnectTimer) clearTimeout(reconnectTimer);
      controller.abort();
    };
  }, [loadSessions]);

  const endSession = useCallback(async (sessionId: string) => {
    await apiFetch(`/sessions/${sessionId}/end`, {
      method: 'POST',
      body: JSON.stringify({ reason: 'Ended by admin panel' })
    });
    await loadSessions();
  }, [loadSessions]);

  const endAllSessions = useCallback(async () => {
    const ids = rows.map((session) => session.sessionId);
    await Promise.all(ids.map((sessionId) => apiFetch(`/sessions/${sessionId}/end`, {
      method: 'POST',
      body: JSON.stringify({ reason: 'Ended all by admin panel' })
    })));
    await loadSessions();
  }, [rows, loadSessions]);

  const metrics = useMemo(() => {
    const activeSessions = rows.length;
    const connectedDevices = rows.reduce((sum, row) => (
      sum
      + (row.connected?.hce ? 1 : 0)
      + (row.connected?.reader ? 1 : 0)
      + (row.connected?.external ? 1 : 0)
    ), 0);
    const apduToday = rows.reduce((sum, row) => sum + (row.apduCount || 0), 0);
    const avgDurationMs = activeSessions === 0 ? 0 : rows.reduce((sum, row) => {
      const startedAt = row.startedAt ? Date.parse(row.startedAt) : NaN;
      return sum + (Number.isFinite(startedAt) ? (Date.now() - startedAt) : 0);
    }, 0) / activeSessions;
    return {
      activeSessions,
      connectedDevices,
      apduToday,
      avgDurationText: `${Math.floor(avgDurationMs / 1000)} 秒`
    };
  }, [rows]);

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
        <div className="rounded border bg-white p-3"><p className="text-xs text-gray-500">活跃 Sessions</p><p className="text-2xl font-semibold">{metrics.activeSessions}</p></div>
        <div className="rounded border bg-white p-3"><p className="text-xs text-gray-500">已连接设备数</p><p className="text-2xl font-semibold">{metrics.connectedDevices}</p></div>
        <div className="rounded border bg-white p-3"><p className="text-xs text-gray-500">今日 APDU 总量</p><p className="text-2xl font-semibold">{metrics.apduToday}</p></div>
        <div className="rounded border bg-white p-3"><p className="text-xs text-gray-500">平均 Session 时长</p><p className="text-2xl font-semibold">{metrics.avgDurationText}</p></div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <button className="rounded border bg-white px-3 py-2" onClick={() => void loadSessions(true)} disabled={loading}>
          {loading ? '刷新中...' : '手动刷新'}
        </button>
        <button className="rounded border border-red-200 bg-red-50 px-3 py-2 text-red-700" onClick={() => void endAllSessions()} disabled={rows.length === 0}>
          结束所有
        </button>
        {message ? <span className="text-sm text-red-600">{message}</span> : null}
      </div>

      <div className="bg-white border rounded overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="p-2 text-left">Token预览</th>
              <th className="p-2 text-left">Mode</th>
              <th className="p-2 text-left">HCE状态</th>
              <th className="p-2 text-left">Reader状态</th>
              <th className="p-2 text-left">External状态</th>
              <th className="p-2 text-left">APDU数</th>
              <th className="p-2 text-left">最后活动</th>
              <th className="p-2 text-left">操作</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((session) => (
              <tr key={session.sessionId} className="border-t">
                <td className="p-2 font-mono text-xs">{session.tokenPreview || session.sessionId.slice(0, 8)}</td>
                <td className="p-2">{session.mode || '-'}</td>
                <td className="p-2"><StatusDot online={session.connected?.hce} /></td>
                <td className="p-2"><StatusDot online={session.connected?.reader} /></td>
                <td className="p-2"><StatusDot online={session.connected?.external} /></td>
                <td className="p-2">{session.apduCount || 0}</td>
                <td className="p-2">{formatRelativeTime(session.lastActivityAt)}</td>
                <td className="p-2">
                  <button className="rounded border border-red-200 px-2 py-1 text-red-700" onClick={() => void endSession(session.sessionId)}>
                    结束 Session
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
