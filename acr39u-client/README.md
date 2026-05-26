# ACR39U Client

Node.js client for ACR39U (PC/SC) to join NFCGate relay sessions as `role=external`.

## Usage

```bash
npm install
npm run dev -- --server ws://localhost:8080/ws/relay --token <relay-token> --baud 9600
```

When a card is inserted into ACR39U, incoming `apdu_command` from the relay session is sent to the smart card via PC/SC and the resulting response is returned as `apdu_response`.
If the relay socket disconnects while a card is still present, the client retries automatically.
