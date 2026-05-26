# ACR39U Relay Bridge

## Install

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Run

```bash
python relay_bridge.py \
  --server ws://localhost:8080/ws/relay \
  --session <SESSION_TOKEN_OR_ID> \
  --jwt <JWT>
```

The bridge listens for `apdu_command`, transmits APDU to the connected PC/SC card, and replies with `apdu_response`.
