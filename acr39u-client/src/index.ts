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

// PC/SC Lite — detect readers
const pcsc = pcsclite();
let activeReader: any = null;
let activeProtocol: number = 0;
let ws: WebSocket | null = null;

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

        // Connect WebSocket after card is ready
        connectWebSocket();
      });
    }

    if (changes & reader.SCARD_STATE_EMPTY && status.state & reader.SCARD_STATE_EMPTY) {
      console.log('💳 Card removed');
      activeReader = null;
      ws?.close();
    }
  });

  reader.on('end', () => {
    console.log('📖 Reader removed');
    activeReader = null;
  });

  reader.on('error', (err: any) => {
    console.error('❌ Reader error:', err.message);
  });
});

pcsc.on('error', (err: any) => {
  console.error('❌ PC/SC error:', err.message);
});

function connectWebSocket() {
  const url = `${opts.server}?token=${opts.token}&role=external`;
  console.log(`🌐 Connecting to ${url}`);

  ws = new WebSocket(url);

  ws.on('open', () => {
    console.log('✅ WebSocket connected as external reader');
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
  });

  ws.on('error', (err: Error) => {
    console.error('❌ WebSocket error:', err.message);
  });
}

function handleMessage(msg: any) {
  switch (msg.type) {
    case 'session_joined':
      console.log(`🔗 Joined session: ${msg.sessionId}`);
      break;

    case 'apdu_command':
      // Received APDU command from HCE → send to real card via reader
      const apdu = hexToBuffer(msg.data);
      console.log(`→ APDU CMD: ${msg.data}`);
      transceive(apdu);
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

function transceive(apdu: Buffer) {
  if (!activeReader) {
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

function hexToBuffer(hex: string): Buffer {
  return Buffer.from(hex, 'hex');
}

// Graceful shutdown
process.on('SIGINT', () => {
  console.log('\n👋 Shutting down...');
  ws?.close();
  pcsc.close();
  process.exit(0);
});
