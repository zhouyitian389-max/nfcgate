import pcsclite from 'pcsclite';
import WebSocket from 'ws';
import { Command } from 'commander';
import dotenv from 'dotenv';

dotenv.config();

const program = new Command();
program
  .option('-s, --server <url>', 'WebSocket server URL', process.env.SERVER_URL || 'ws://localhost:8080/ws/relay')
  .option('-t, --token <token>', 'Relay token', process.env.RELAY_TOKEN || '')
  .parse();

const opts = program.opts();

if (!opts.token) {
  console.error('❌ Relay token is required. Use --token or set RELAY_TOKEN env var.');
  process.exit(1);
}

console.log('🔌 ACR39U NFCGate Client');
console.log(`   Server: ${opts.server}`);
console.log('   Waiting for smart card reader...\n');

const pcsc = pcsclite();
let activeReader: any = null;
let activeProtocol = 0;
let ws: WebSocket | null = null;
let reconnectTimer: NodeJS.Timeout | null = null;
let reconnectDelayMs = 3_000;

pcsc.on('reader', (reader: any) => {
  console.log(`📖 Reader detected: ${reader.name}`);

  reader.on('status', (status: any) => {
    const changes = reader.state ^ status.state;

    if ((changes & reader.SCARD_STATE_PRESENT) && (status.state & reader.SCARD_STATE_PRESENT)) {
      console.log('💳 Card inserted');
      if (status.atr) {
        console.log(`   ATR: ${Buffer.from(status.atr).toString('hex').toUpperCase()}`);
      }

      reader.connect({ share_mode: reader.SCARD_SHARE_SHARED }, (err: any, protocol: number) => {
        if (err) {
          console.error('❌ Connect error:', err.message);
          scheduleReconnect();
          return;
        }

        activeReader = reader;
        activeProtocol = protocol;
        reconnectDelayMs = 3_000;
        console.log(`✅ Card connected (protocol: ${describeProtocol(reader, protocol)})`);
        connectWebSocket();
      });
    }

    if ((changes & reader.SCARD_STATE_EMPTY) && (status.state & reader.SCARD_STATE_EMPTY)) {
      console.log('💳 Card removed');
      activeReader = null;
      activeProtocol = 0;
      clearReconnect();
      ws?.close(1000, 'card_removed');
    }
  });

  reader.on('end', () => {
    console.log('📖 Reader removed');
    activeReader = null;
    activeProtocol = 0;
    clearReconnect();
    ws?.close(1000, 'reader_removed');
  });

  reader.on('error', (err: any) => {
    console.error('❌ Reader error:', err.message);
    scheduleReconnect();
  });
});

pcsc.on('error', (err: any) => {
  console.error('❌ PC/SC error:', err.message);
});

function connectWebSocket() {
  if (!activeReader) {
    return;
  }
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
    return;
  }

  clearReconnect();
  const url = `${opts.server}?token=${opts.token}&role=external`;
  console.log(`🌐 Connecting to ${url}`);

  ws = new WebSocket(url);

  ws.on('open', () => {
    reconnectDelayMs = 3_000;
    console.log('✅ WebSocket connected as external reader');
  });

  ws.on('message', (data: Buffer) => {
    try {
      const msg = JSON.parse(data.toString());
      handleMessage(msg);
    } catch {
      console.error('❌ Invalid message:', data.toString());
    }
  });

  ws.on('close', (code: number, reason: Buffer) => {
    console.log(`🔌 WebSocket closed: ${code} ${reason.toString()}`);
    ws = null;
    scheduleReconnect();
  });

  ws.on('error', (err: Error) => {
    console.error('❌ WebSocket error:', err.message);
  });
}

function scheduleReconnect() {
  if (!activeReader || reconnectTimer) {
    return;
  }

  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connectWebSocket();
  }, reconnectDelayMs);
  reconnectDelayMs = Math.min(reconnectDelayMs * 2, 30_000);
}

function clearReconnect() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
}

function handleMessage(msg: any) {
  switch (msg.type) {
    case 'session_joined':
      console.log(`🔗 Joined session: ${msg.sessionId}`);
      break;
    case 'apdu_command': {
      const apdu = hexToBuffer(msg.data);
      console.log(`→ APDU CMD: ${msg.data}`);
      transceive(apdu);
      break;
    }
    case 'session_end':
      console.log('📴 Session ended');
      ws?.close(1000, 'session_end');
      break;
    case 'error':
      console.error(`❌ Server error: ${msg.message}`);
      break;
    default:
      break;
  }
}

function transceive(apdu: Buffer) {
  if (!activeReader || !activeProtocol) {
    console.error('❌ No card connected');
    sendResponse(Buffer.from([0x6F, 0x00]));
    return;
  }

  activeReader.transmit(apdu, 256, activeProtocol, (err: any, response: Buffer) => {
    if (err) {
      console.error('❌ Transmit error:', err.message);
      sendResponse(Buffer.from([0x6F, 0x00]));
      return;
    }

    console.log(`← APDU RSP: ${response.toString('hex').toUpperCase()}`);
    sendResponse(response);
  });
}

function sendResponse(response: Buffer) {
  if (!ws || ws.readyState !== WebSocket.OPEN) return;

  const msg = JSON.stringify({
    type: 'apdu_response',
    data: response.toString('hex').toUpperCase()
  });
  ws.send(msg);
}

function describeProtocol(reader: any, protocol: number) {
  const labels: string[] = [];
  if (protocol & reader.SCARD_PROTOCOL_T0) labels.push('T=0');
  if (protocol & reader.SCARD_PROTOCOL_T1) labels.push('T=1');
  return labels.length ? labels.join('/') : `0x${protocol.toString(16)}`;
}

function hexToBuffer(hex: string): Buffer {
  return Buffer.from(hex, 'hex');
}

process.on('SIGINT', () => {
  console.log('\n👋 Shutting down...');
  clearReconnect();
  ws?.close();
  pcsc.close();
  process.exit(0);
});
