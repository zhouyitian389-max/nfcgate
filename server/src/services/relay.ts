import type { Server } from 'http';
import { WebSocketServer, type WebSocket } from 'ws';
import { v4 as uuidv4 } from 'uuid';
import prisma from '../db.js';
import { sseHub } from './sseHub.js';

type RelayRole = 'hce' | 'reader' | 'external';

type RelayMessage =
  | { type: 'session_joined'; sessionId: string }
  | { type: 'apdu_command'; data: string }
  | { type: 'apdu_response'; data: string }
  | { type: 'session_end'; reason: string }
  | { type: 'error'; message: string }
  | { type: 'ping' }
  | { type: 'pong' };

interface SessionTokenMeta {
  token: string;
  accountId: string;
  cardId?: string;
  mode: string;
  expiresAt: Date;
}

interface SocketWithState extends WebSocket {
  isAlive?: boolean;
}

interface RelaySession {
  token: string;
  sessionId: string;
  accountId?: string;
  cardId?: string;
  mode: string;
  logId?: string;
  apduCount: number;
  startedAt: number;
  lastActivityAt: number;
  hce?: SocketWithState;
  reader?: SocketWithState;
  external?: SocketWithState;
}

const SESSION_TIMEOUT_MS = 30_000;
const HEARTBEAT_MS = 15_000;

const sessions = new Map<string, RelaySession>();
const sessionTokens = new Map<string, SessionTokenMeta>();

export async function createSessionToken(accountId: string, cardId?: string, mode = 'NFC_RELAY') {
  const token = uuidv4();
  const expiresAt = new Date(Date.now() + 60 * 60 * 1000);
  sessionTokens.set(token, { token, accountId, cardId, mode, expiresAt });
  return { token, expiresAt };
}

async function resolveSessionMeta(token: string): Promise<Pick<RelaySession, 'accountId' | 'cardId' | 'mode'>> {
  const meta = sessionTokens.get(token);
  if (meta && meta.expiresAt.getTime() > Date.now()) {
    return { accountId: meta.accountId, cardId: meta.cardId, mode: meta.mode };
  }

  const card = await prisma.card.findFirst({
    where: {
      relayToken: token,
      OR: [{ relayTokenExpiresAt: null }, { relayTokenExpiresAt: { gt: new Date() } }]
    },
    select: { id: true, accountId: true }
  });

  if (card) {
    return { accountId: card.accountId, cardId: card.id, mode: 'NFC_RELAY' };
  }

  return { mode: 'NFC_RELAY' };
}

function send(ws: WebSocket | undefined, message: RelayMessage) {
  if (!ws || ws.readyState !== ws.OPEN) return;
  ws.send(JSON.stringify(message));
}

function getReaderPeer(session: RelaySession): WebSocket | undefined {
  return session.reader ?? session.external;
}

async function closeSession(token: string, reason: string) {
  const session = sessions.get(token);
  if (!session) return;

  send(session.hce, { type: 'session_end', reason });
  send(session.reader, { type: 'session_end', reason });
  send(session.external, { type: 'session_end', reason });

  for (const ws of [session.hce, session.reader, session.external]) {
    if (ws && ws.readyState === ws.OPEN) ws.close(1000, reason);
  }

  sessions.delete(token);
  if (session.accountId) {
    sseHub.sendToUser(session.accountId, 'relay_session_end', {
      sessionId: session.sessionId,
      token,
      reason,
      apduCount: session.apduCount
    });
  }

  if (session.logId) {
    const duration = Date.now() - session.startedAt;
    await prisma.apduLog.update({
      where: { id: session.logId },
      data: { apduCount: session.apduCount, duration, closedReason: reason }
    }).catch(() => undefined);
  }
}

async function ensureSession(token: string): Promise<RelaySession> {
  const existing = sessions.get(token);
  if (existing) return existing;

  const meta = await resolveSessionMeta(token);
  const session: RelaySession = {
    token,
    sessionId: uuidv4(),
    accountId: meta.accountId,
    cardId: meta.cardId,
    mode: meta.mode,
    startedAt: Date.now(),
    lastActivityAt: Date.now(),
    apduCount: 0
  };

  if (session.accountId) {
    const created = await prisma.apduLog.create({
      data: {
        accountId: session.accountId,
        cardId: session.cardId,
        sessionId: session.sessionId,
        mode: session.mode,
        apduCount: 0,
        duration: 0
      },
      select: { id: true }
    }).catch(() => null);
    session.logId = created?.id;

    sseHub.sendToUser(session.accountId, 'relay_session_start', {
      sessionId: session.sessionId,
      token
    });
  }

  sessions.set(token, session);
  return session;
}

function setRoleSocket(session: RelaySession, role: RelayRole, ws: SocketWithState) {
  if (role === 'hce') {
    if (session.hce && session.hce !== ws) session.hce.close(1000, 'Replaced by new hce connection');
    session.hce = ws;
  } else if (role === 'reader') {
    if (session.reader && session.reader !== ws) session.reader.close(1000, 'Replaced by new reader connection');
    session.reader = ws;
  } else {
    if (session.external && session.external !== ws) session.external.close(1000, 'Replaced by new external connection');
    session.external = ws;
  }
}

function removeRoleSocket(session: RelaySession, role: RelayRole, ws: SocketWithState) {
  if (role === 'hce' && session.hce === ws) session.hce = undefined;
  if (role === 'reader' && session.reader === ws) session.reader = undefined;
  if (role === 'external' && session.external === ws) session.external = undefined;
}

function hasAnyPeer(session: RelaySession) {
  return Boolean(session.hce || session.reader || session.external);
}

function parseMessage(raw: string): RelayMessage | null {
  try {
    return JSON.parse(raw) as RelayMessage;
  } catch {
    return null;
  }
}

export function getActiveSessions(accountId?: string) {
  const list = Array.from(sessions.values()).map((s) => ({
    token: s.token,
    sessionId: s.sessionId,
    accountId: s.accountId,
    cardId: s.cardId,
    mode: s.mode,
    apduCount: s.apduCount,
    connected: {
      hce: Boolean(s.hce),
      reader: Boolean(s.reader),
      external: Boolean(s.external)
    },
    startedAt: new Date(s.startedAt).toISOString(),
    lastActivityAt: new Date(s.lastActivityAt).toISOString()
  }));

  if (!accountId) return list;
  return list.filter((s) => s.accountId === accountId);
}

export function initWebSocket(server: Server) {
  const wss = new WebSocketServer({ noServer: true });

  server.on('upgrade', (request, socket, head) => {
    const url = new URL(request.url || '', `http://${request.headers.host}`);
    if (url.pathname !== '/ws/relay') {
      socket.destroy();
      return;
    }

    const token = url.searchParams.get('token');
    const role = url.searchParams.get('role') as RelayRole | null;

    if (!token || !role || !['hce', 'reader', 'external'].includes(role)) {
      socket.write('HTTP/1.1 400 Bad Request\r\n\r\n');
      socket.destroy();
      return;
    }

    wss.handleUpgrade(request, socket, head, (ws) => {
      wss.emit('connection', ws, token, role);
    });
  });

  wss.on('connection', async (rawWs: WebSocket, token: string, role: RelayRole) => {
    const ws = rawWs as SocketWithState;
    ws.isAlive = true;

    const session = await ensureSession(token);
    session.lastActivityAt = Date.now();
    setRoleSocket(session, role, ws);
    send(ws, { type: 'session_joined', sessionId: session.sessionId });

    ws.on('pong', () => {
      ws.isAlive = true;
      const current = sessions.get(token);
      if (current) current.lastActivityAt = Date.now();
    });

    ws.on('message', async (buf) => {
      const message = parseMessage(buf.toString());
      if (!message) {
        send(ws, { type: 'error', message: 'Invalid JSON message' });
        return;
      }

      const current = sessions.get(token);
      if (!current) {
        send(ws, { type: 'error', message: 'Session not found' });
        return;
      }

      current.lastActivityAt = Date.now();

      if (message.type === 'ping') {
        send(ws, { type: 'pong' });
        return;
      }

      if (message.type === 'apdu_command') {
        if (role !== 'hce') {
          send(ws, { type: 'error', message: 'Only hce can send apdu_command' });
          return;
        }

        const target = getReaderPeer(current);
        if (!target) {
          send(ws, { type: 'error', message: 'No reader connected' });
          return;
        }

        current.apduCount += 1;
        send(target, { type: 'apdu_command', data: message.data });
      }

      if (message.type === 'apdu_response') {
        if (role !== 'reader' && role !== 'external') {
          send(ws, { type: 'error', message: 'Only reader/external can send apdu_response' });
          return;
        }

        send(current.hce, { type: 'apdu_response', data: message.data });
      }

      if (current.logId) {
        await prisma.apduLog.update({
          where: { id: current.logId },
          data: { apduCount: current.apduCount, duration: Date.now() - current.startedAt }
        }).catch(() => undefined);
      }
    });

    ws.on('close', async () => {
      const current = sessions.get(token);
      if (!current) return;

      removeRoleSocket(current, role, ws);
      if (!hasAnyPeer(current)) {
        await closeSession(token, `${role} disconnected`);
      }
    });

    ws.on('error', async () => {
      await closeSession(token, `${role} socket error`);
    });
  });

  setInterval(async () => {
    for (const [token, session] of sessions.entries()) {
      if (Date.now() - session.lastActivityAt > SESSION_TIMEOUT_MS) {
        await closeSession(token, 'Session timeout');
      }
    }

    wss.clients.forEach((client) => {
      const ws = client as SocketWithState;
      if (!ws.isAlive) {
        ws.terminate();
        return;
      }
      ws.isAlive = false;
      ws.ping();
    });
  }, HEARTBEAT_MS).unref();
}
