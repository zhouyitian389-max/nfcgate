import type { IncomingMessage, Server } from 'http';
import { WebSocketServer, type WebSocket } from 'ws';
import { v4 as uuidv4 } from 'uuid';
import prisma from '../db.js';
import { sseHub } from './sseHub.js';

type RelayRole = 'hce' | 'reader' | 'external';

type RelayMessage =
  | { type: 'session_join'; sessionId?: string; token?: string; role?: RelayRole; atr?: string }
  | { type: 'session_paired'; sessionId: string; atr?: string }
  | { type: 'session_joined'; sessionId: string; tokenPreview?: string }
  | { type: 'apdu_command'; data: string; seq?: number }
  | { type: 'apdu_response'; data: string; seq?: number }
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
  lastCommandSeq: number;
  lastResponseSeq: number;
  readerAtr?: string;
  hce?: SocketWithState;
  reader?: SocketWithState;
  external?: SocketWithState;
}

const SESSION_TIMEOUT_MS = Number(process.env.RELAY_SESSION_TIMEOUT_MS || 20_000);
const HEARTBEAT_MS = Number(process.env.RELAY_HEARTBEAT_MS || 10_000);
const HEX_REGEX = /^[0-9A-Fa-f]+$/;
const MAX_APDU_LEN = 2000;
const MAX_ATR_LEN = 128;

const sessions = new Map<string, RelaySession>();
const sessionTokens = new Map<string, SessionTokenMeta>();
const wsConnectionByIp = new Map<string, number>();

function isValidHex(data: unknown, maxLength: number): data is string {
  return typeof data === 'string' &&
    data.length > 0 &&
    data.length <= maxLength &&
    data.length % 2 === 0 &&
    HEX_REGEX.test(data);
}

function isValidApduHex(data: unknown): data is string {
  return isValidHex(data, MAX_APDU_LEN);
}

function isValidAtrHex(data: unknown): data is string {
  return isValidHex(data, MAX_ATR_LEN);
}

function tokenPreview(token: string) {
  return `${token.slice(0, 4)}…${token.slice(-4)}`;
}

function sequenceIsNext(lastSeen: number, seq: unknown): seq is number {
  return typeof seq === 'number' && Number.isInteger(seq) && seq > lastSeen;
}

function extractBearerToken(request: IncomingMessage) {
  const authHeader = request.headers.authorization;
  if (!authHeader?.startsWith('Bearer ')) {
    return undefined;
  }
  const token = authHeader.slice('Bearer '.length).trim();
  return token || undefined;
}

export async function createSessionToken(accountId: string, cardId?: string, mode = 'NFC_RELAY') {
  if (cardId) {
    const card = await prisma.card.findFirst({
      where: { id: cardId, accountId },
      select: { id: true }
    });
    if (!card) {
      throw new Error('card_not_found');
    }
  }

  const token = uuidv4();
  const expiresAt = new Date(Date.now() + 60 * 60 * 1000);
  sessionTokens.set(token, { token, accountId, cardId, mode, expiresAt });
  return { token, expiresAt };
}

async function resolveSessionMeta(token: string): Promise<Pick<RelaySession, 'accountId' | 'cardId' | 'mode'> | null> {
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

  return null;
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

export async function endSessionById(sessionId: string, accountId?: string, reason = 'Ended by API') {
  const session = Array.from(sessions.values()).find((item) => item.sessionId === sessionId && (!accountId || item.accountId === accountId));
  if (!session) {
    return false;
  }
  await closeSession(session.token, reason);
  return true;
}

async function ensureSession(token: string): Promise<RelaySession> {
  const existing = sessions.get(token);
  if (existing) return existing;

  const meta = await resolveSessionMeta(token);
  if (!meta) {
    throw new Error('token_expired');
  }
  const session: RelaySession = {
    token,
    sessionId: uuidv4(),
    accountId: meta.accountId,
    cardId: meta.cardId,
    mode: meta.mode,
    startedAt: Date.now(),
    lastActivityAt: Date.now(),
    apduCount: 0,
    lastCommandSeq: 0,
    lastResponseSeq: 0
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
      sessionId: session.sessionId
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

function getRoleSocket(session: RelaySession, role: RelayRole) {
  if (role === 'hce') return session.hce;
  if (role === 'reader') return session.reader;
  return session.external;
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
    tokenPreview: tokenPreview(s.token),
    sessionId: s.sessionId,
    accountId: s.accountId,
    cardId: s.cardId,
    mode: s.mode,
    apduCount: s.apduCount,
    atr: s.readerAtr,
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
    const ip = request.socket.remoteAddress || 'unknown';
    const now = Date.now();
    const last = wsConnectionByIp.get(ip) ?? 0;
    if (now - last < 1000) {
      socket.destroy();
      return;
    }
    wsConnectionByIp.set(ip, now);

    const token = extractBearerToken(request);
    const role = (url.searchParams.get('role') as RelayRole | null) || undefined;

    wss.handleUpgrade(request, socket, head, (ws) => {
      wss.emit('connection', ws, token, role);
    });
  });

  wss.on('connection', async (rawWs: WebSocket, initialToken?: string, initialRole?: RelayRole) => {
    const ws = rawWs as SocketWithState;
    ws.isAlive = true;
    let token = initialToken;
    let role = initialRole;

    const bindSession = async (joinToken: string, joinRole: RelayRole, atr?: string) => {
      token = joinToken;
      role = joinRole;
      let session: RelaySession;
      try {
        session = await ensureSession(joinToken);
      } catch {
        send(ws, { type: 'error', message: 'token_expired' });
        ws.close(4001, 'token_expired');
        return false;
      }
      session.lastActivityAt = Date.now();
      if (atr && isValidAtrHex(atr) && joinRole !== 'hce') {
        session.readerAtr = atr.toUpperCase();
      }
      setRoleSocket(session, joinRole, ws);
      send(ws, { type: 'session_joined', sessionId: session.sessionId, tokenPreview: tokenPreview(joinToken) });
      if (session.hce && getReaderPeer(session)) {
        send(session.hce, { type: 'session_paired', sessionId: session.sessionId, atr: session.readerAtr });
        send(session.reader, { type: 'session_paired', sessionId: session.sessionId, atr: session.readerAtr });
        send(session.external, { type: 'session_paired', sessionId: session.sessionId, atr: session.readerAtr });
      }
      return true;
    };

    if (token && role && ['hce', 'reader', 'external'].includes(role)) {
      await bindSession(token, role);
    }

    ws.on('pong', () => {
      ws.isAlive = true;
      const current = token ? sessions.get(token) : undefined;
      if (current) current.lastActivityAt = Date.now();
    });

    ws.on('message', async (buf) => {
      const message = parseMessage(buf.toString());
      if (!message) {
        send(ws, { type: 'error', message: 'Invalid JSON message' });
        return;
      }

      if (message.type === 'session_join') {
        const joinToken = message.token || message.sessionId;
        const joinRole = message.role;
        if (!joinToken || !joinRole || !['hce', 'reader', 'external'].includes(joinRole)) {
          send(ws, { type: 'error', message: 'session_join requires token/sessionId and role' });
          return;
        }
        if (message.atr && !isValidAtrHex(message.atr)) {
          send(ws, { type: 'error', message: 'Invalid ATR format' });
          return;
        }
        await bindSession(joinToken, joinRole, message.atr);
        return;
      }

      if (!token || !role) {
        send(ws, { type: 'error', message: 'Join session first' });
        return;
      }

      const current = sessions.get(token);
      if (!current) {
        send(ws, { type: 'error', message: 'Session not found' });
        return;
      }
      if (getRoleSocket(current, role) !== ws) {
        send(ws, { type: 'error', message: 'Socket is no longer active for this role' });
        return;
      }

      current.lastActivityAt = Date.now();

      if (message.type === 'ping') {
        send(ws, { type: 'pong' });
        return;
      }

      if (message.type === 'session_end') {
        await closeSession(token, message.reason || `${role} requested end`);
        return;
      }

      if (message.type === 'apdu_command') {
        if (role !== 'hce') {
          send(ws, { type: 'error', message: 'Only hce can send apdu_command' });
          return;
        }
        if (!isValidApduHex(message.data)) {
          send(ws, { type: 'error', message: 'Invalid APDU command format' });
          return;
        }
        if (!sequenceIsNext(current.lastCommandSeq, message.seq)) {
          send(ws, { type: 'error', message: 'Invalid APDU command sequence' });
          return;
        }

        const target = getReaderPeer(current);
        if (!target) {
          send(ws, { type: 'error', message: 'No reader connected' });
          return;
        }

        current.lastCommandSeq = message.seq;
        current.apduCount += 1;
        console.info(`[relay] APDU command ${current.sessionId} from hce`);
        send(target, { type: 'apdu_command', data: message.data, seq: message.seq });
      }

      if (message.type === 'apdu_response') {
        if (role !== 'reader' && role !== 'external') {
          send(ws, { type: 'error', message: 'Only reader/external can send apdu_response' });
          return;
        }
        if (!isValidApduHex(message.data)) {
          send(ws, { type: 'error', message: 'Invalid APDU response format' });
          return;
        }
        if (!sequenceIsNext(current.lastResponseSeq, message.seq)) {
          send(ws, { type: 'error', message: 'Invalid APDU response sequence' });
          return;
        }
        if (message.seq !== current.lastCommandSeq) {
          send(ws, { type: 'error', message: 'APDU response sequence mismatch' });
          return;
        }
        if (!current.hce || current.hce.readyState !== current.hce.OPEN) {
          send(ws, { type: 'error', message: 'No hce connected' });
          console.warn(`[relay] Dropped APDU response ${current.sessionId}: no hce peer`);
          return;
        }
        current.lastResponseSeq = message.seq;
        console.info(`[relay] APDU response ${current.sessionId} from ${role}`);
        send(current.hce, { type: 'apdu_response', data: message.data, seq: message.seq });
      }

      if (current.logId) {
        await prisma.apduLog.update({
          where: { id: current.logId },
          data: { apduCount: current.apduCount, duration: Date.now() - current.startedAt }
        }).catch(() => undefined);
      }
    });

    ws.on('close', async () => {
      if (!token || !role) return;
      const current = sessions.get(token);
      if (!current) return;

      removeRoleSocket(current, role, ws);
      if (!hasAnyPeer(current)) {
        await closeSession(token, `${role} disconnected`);
      }
    });

    ws.on('error', async () => {
      if (!token || !role) return;
      const current = sessions.get(token);
      if (!current) return;
      removeRoleSocket(current, role, ws);
      if (!hasAnyPeer(current)) {
        await closeSession(token, `${role} socket error`);
      }
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

    const now = Date.now();
    for (const [ip, ts] of wsConnectionByIp.entries()) {
      if (now - ts > 10_000) {
        wsConnectionByIp.delete(ip);
      }
    }
    for (const [token, meta] of sessionTokens.entries()) {
      if (meta.expiresAt.getTime() <= now) {
        sessionTokens.delete(token);
      }
    }
  }, HEARTBEAT_MS).unref();
}
