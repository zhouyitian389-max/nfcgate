# NFC Relay Cloud Platform Deployment

## Prerequisites

- Docker + Docker Compose
- Node.js 20+ (for local dev)
- Python 3.10+ (for ACR39U bridge)
- Android Studio / Gradle (for Android apps)

## Architecture

- `server/`: Express + WebSocket relay + JWT + Prisma/PostgreSQL
- `admin-panel/`: Next.js 14 admin console
- `app-hce/`: Android HCE relay endpoint
- `app-reader/`: Android reader relay endpoint
- `client-acr39u/`: Python PC/SC relay bridge

## Setup

1. Copy environment template:
   ```bash
   cp .env.example .env
   ```
2. Start infra:
   ```bash
   docker-compose up -d
   ```
3. Run Prisma setup (if needed):
   ```bash
   cd server
   npm install
   npx prisma generate
   npx prisma db push
   ```

## Usage

- Server API: `http://localhost:8080/api`
- WebSocket relay: `ws://localhost:8080/ws/relay`
- Admin panel: `http://localhost:3001`

### Android HCE/Reader

- Configure `relay_ws_url`, `relay_jwt`, `relay_session_id` preferences.
- HCE app sends `apdu_command` and waits for `apdu_response`.
- Reader app receives `apdu_command`, transceives over `IsoDep`, and returns `apdu_response`.

### ACR39U Client

```bash
cd client-acr39u
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python relay_bridge.py --server ws://localhost:8080/ws/relay --session <SESSION> --jwt <JWT>
```
