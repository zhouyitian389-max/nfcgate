# NFCGate v2 Deployment

## 1. Configure environment

```bash
cp .env.example .env
```

Set at least:

- `JWT_SECRET` to a long random value
- `CORS_ORIGIN` to the admin panel origin
- `DATABASE_URL` to your PostgreSQL instance

## 2. Start PostgreSQL and server

```bash
docker-compose up -d --build
```

The server container runs:

1. `prisma migrate deploy`
2. fallback `prisma db push` when no migrations exist
3. `node dist/index.js`

## 3. Start the admin panel

```bash
cd admin
npm install
npm run dev
```

## 4. Clients

- HCE app: `/home/runner/work/nfcgate/nfcgate/android/hce-app`
- Reader app: `/home/runner/work/nfcgate/nfcgate/android/reader-app`
- ACR39U client: `/home/runner/work/nfcgate/nfcgate/acr39u-client`
