import pcsclite from 'pcsclite';
import WebSocket from 'ws';
import { Command } from 'commander';
import dotenv from 'dotenv';

dotenv.config();

const program = new Command();
program
  .option('-s, --server <url>', 'WebSocket server URL', process.env.SERVER_URL || 'ws://localhost:8080/ws/relay')
  .option('-t, --token <token>', 'Relay token', process.env.RELAY_TOKEN || '')
  .option('-b, --baud <rate>', 'Reader baud rate (best effort)', process.env.ACR39U_BAUD_RATE || '9600')
  .parse();

const opts = program.opts();

if (!opts.token) {
  console.error('❌ Relay token is required. Use --token or set RELAY_TOKEN env var.');
  process.exit(1);
}

console.log('🔌 ACR39U NFCGate Client');
console.log(`   Server: ${opts.server}`);
console.log('   Waiting for smart card reader...\n');

// PC/SC Lite — detect readers
const pcsc = pcsclite();
let activeReader: any = null;
let activeProtocol: number = 0;
let ws: WebSocket | null = null;
let reconnectTimer: NodeJS.Timeout | null = null;
let joinedSessionId = '';
const ACR39U_IOCTL = 0x42000DAC;
const APDU_BUFFER_SIZE = 4096;
const SW_NO_CARD = Buffer.from([0x6A, 0x82]);
const SW_TRANSMIT_ERROR = Buffer.from([0x6F, 0x00]);

pcsc.on('reader', (reader: any) => {
  console.log(`📖 Reader detected: ${reader.name}`);

  reader.on('status', (status: any) => {
    const changes = reader.state ^ status.state;

    if (changes & reader.SCARD_STATE_PRESENT && status.state & reader.SCARD_STATE_PRESENT) {
      console.log('💳 Card inserted');

      reader.connect({ share_mode: reader.SCARD_SHARE_SHARED }, (err: any, protocol: number) => {
        if (err) {
          console.error('❌ Connect error:', err.message);
          return;
        }

        activeReader = reader;
        activeProtocol = protocol;
        console.log(`✅ Card connected (protocol: ${protocol === 1 ? 'T=0' : 'T=1'})`);

        configureReader().finally(() => {
          // Connect WebSocket after card is ready
          connectWebSocket();
        });
      });
    }

    if (changes & reader.SCARD_STATE_EMPTY && status.state & reader.SCARD_STATE_EMPTY) {
      console.log('💳 Card removed');
      activeReader = null;
      ws?.close();
      clearReconnectTimer();
    }
  });

  reader.on('end', () => {
    console.log('📖 Reader removed');
    activeReader = null;
    ws?.close();
    clearReconnectTimer();
  });

  reader.on('error', (err: any) => {
    console.error('❌ Reader error:', err.message);
  });
});

pcsc.on('error', (err: any) => {
  console.error('❌ PC/SC error:', err.message);
});

function connectWebSocket() {
  if (!activeReader) return;
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return;

  console.log(`🌐 Connecting to ${opts.server}`);
  ws = new WebSocket(opts.server, {
    headers: {
      Authorization: 'Bearer' + ' ' + opts.token
    }
  });

  ws.on('open', () => {
    console.log('✅ WebSocket connected as external reader');
    ws?.send(JSON.stringify({
      type: 'session_join',
      token: opts.token,
      role: 'external'
    }));
  });

  ws.on('message', (data: Buffer) => {
    try {
      const msg = JSON.parse(data.toString());
      handleMessage(msg);
    } catch (e) {
      console.error('❌ Invalid message:', data.toString());
    }
  });

  ws.on('close', (code: number, reason: Buffer) => {
    console.log(`🔌 WebSocket closed: ${code} ${reason.toString()}`);
    if (activeReader) {
      scheduleReconnect();
    }
  });

  ws.on('error', (err: Error) => {
    console.error('❌ WebSocket error:', err.message);
    if (activeReader) {
      scheduleReconnect();
    }
  });
}

function scheduleReconnect() {
  if (reconnectTimer) return;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connectWebSocket();
  }, 2000);
}

function clearReconnectTimer() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
}

async function configureReader() {
  const baudRate = Number(opts.baud || 9600);
  if (!activeReader || !Number.isFinite(baudRate) || baudRate <= 0) return;
  if (!String(activeReader.name || '').toUpperCase().includes('ACR39U')) return;
  if (baudRate !== 9600) {
    console.log(`ℹ️ Requested baud rate ${baudRate} (ACR39U vendor commands currently optimized for 9600)`);
  }

  await new Promise<void>((resolve) => {
    // ACS escape command (best effort): set PICC polling/communication parameters for 9600-bps compatible cards.
    const command = Buffer.from('FF00517F00', 'hex');
    activeReader.control(command, ACR39U_IOCTL, APDU_BUFFER_SIZE, (err: any) => {
      if (err) {
        console.warn(`⚠️ Unable to apply ACR39U 9600-bps profile: ${err.message}`);
      } else {
        console.log('✅ ACR39U 9600-bps profile applied');
      }
      resolve();
    });
  });
}

function handleMessage(msg: any) {
  switch (msg.type) {
    case 'session_joined':
      joinedSessionId = typeof msg.sessionId === 'string' ? msg.sessionId : '';
      console.log(`🔗 Joined session: ${joinedSessionId}`);
      break;

    case 'apdu_command':
      // Received APDU command from HCE → send to real card via reader
      const apdu = hexToBuffer(msg.data);
      console.log(`→ APDU CMD: ${msg.data}`);
      transceive(apdu, Number.isInteger(msg.seq) ? msg.seq : 0);
      break;

    case 'session_end':
      console.log('📴 Session ended');
      ws?.close();
      break;

    case 'error':
      console.error(`❌ Server error: ${msg.message}`);
      break;
  }
}

function transceive(apdu: Buffer, seq: number) {
  if (!activeReader) {
    console.error('❌ No card connected');
    sendResponse(SW_NO_CARD, seq);
    return;
  }

  transmitWithGetResponse(apdu)
    .then((response) => {
      console.log(`← APDU RSP: ${response.toString('hex').toUpperCase()}`);
      sendResponse(response, seq);
    })
    .catch((err: Error) => {
      console.error('❌ Transmit error:', err.message);
      sendResponse(SW_TRANSMIT_ERROR, seq);
    });
}

function sendResponse(response: Buffer, seq: number) {
  if (!ws || ws.readyState !== WebSocket.OPEN) return;

  const msg = JSON.stringify({
    type: 'apdu_response',
    sessionId: joinedSessionId || opts.token,
    seq,
    data: response.toString('hex').toUpperCase()
  });
  ws.send(msg);
}

function hexToBuffer(hex: string): Buffer {
  return Buffer.from(hex, 'hex');
}

function transmitApdu(apdu: Buffer): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    if (!activeReader) {
      reject(new Error('No card connected'));
      return;
    }

    activeReader.transmit(apdu, APDU_BUFFER_SIZE, activeProtocol, (err: any, response: Buffer) => {
      if (err) {
        reject(err);
        return;
      }
      resolve(response);
    });
  });
}

async function transmitWithGetResponse(apdu: Buffer): Promise<Buffer> {
  let response = await transmitApdu(apdu);
  let parts = [response.slice(0, Math.max(0, response.length - 2))];
  let guard = 0;

  while (response.length >= 2 && response[response.length - 2] === 0x61 && guard < 16) {
    const remaining = response[response.length - 1] || 0x00;
    response = await transmitApdu(Buffer.from([0x00, 0xC0, 0x00, 0x00, remaining]));
    parts.push(response.slice(0, Math.max(0, response.length - 2)));
    guard += 1;
  }

  const statusWord = response.length >= 2 ? response.slice(-2) : SW_TRANSMIT_ERROR;
  return Buffer.concat([...parts, statusWord]);
}

// Graceful shutdown
process.on('SIGINT', () => {
  console.log('\n👋 Shutting down...');
  ws?.close();
  clearReconnectTimer();
  pcsc.close();
  process.exit(0);
});
