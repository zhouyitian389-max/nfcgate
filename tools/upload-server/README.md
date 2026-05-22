# NFCGate upload server

This sample server accepts `.nfcg.json` session uploads, stores them in SQLite, parses EMV/MIFARE data, and exposes simple REST and HTML views.

## Run

```bash
python -m nfcgate_server --host 127.0.0.1 --port 8080 --db ./nfcgate.sqlite3 --api-token secret-token
```

## Endpoints

- `POST /api/v1/sessions`
- `GET /api/v1/sessions/<id>/parsed`
- `GET /api/v1/parsed`
- `GET /api/v1/audit`
