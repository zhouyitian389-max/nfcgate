from __future__ import annotations

import argparse
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from .audit import tail_audit, write_audit_line
from .models import ParsedRecord, SessionRecord
from .parsers.pipeline import PARSER_VERSION, parse_session
from .storage import Storage


class ServerConfig:
    def __init__(self, db_path: str, api_token: str, admin_token: str | None = None, audit_path: str = "audit.log") -> None:
        self.db_path = db_path
        self.api_token = api_token
        self.admin_token = admin_token
        self.audit_path = Path(audit_path)


class UploadHandler(BaseHTTPRequestHandler):
    config: ServerConfig
    storage: Storage

    def do_POST(self) -> None:
        if not self._authorized(self.config.api_token):
            self._send(401, {"error": "unauthorized"})
            return
        if self.path != "/api/v1/sessions":
            self._send(404, {"error": "not found"})
            return
        payload = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))).decode("utf-8"))
        session_id = payload.get("session_id") or payload.get("sessionId")
        if not session_id:
            self._send(400, {"error": "missing session id"})
            return
        self.storage.save_session(SessionRecord(session_id=session_id, payload=payload))
        parsed = parse_session(payload)
        self.storage.save_parsed(
            ParsedRecord(
                session_id=session_id,
                kind=parsed.kind,
                summary_json=parsed.summary_json,
                raw_json=parsed.raw_json,
                warnings=parsed.warnings,
                parser_version=PARSER_VERSION,
            )
        )
        self._send(201, {"session_id": session_id, "kind": parsed.kind})

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path.startswith("/api/v1/sessions/") and parsed.path.endswith("/parsed"):
            session_id = parsed.path.split("/")[-2]
            row = self.storage.get_parsed(session_id)
            if row is None:
                self._send(404, {"error": "not found"})
                return
            params = parse_qs(parsed.query)
            if params.get("unmask") == ["1"]:
                if not self.config.admin_token:
                    self._send(503, {"error": "Admin token not configured"})
                    return
                if not self._authorized(self.config.admin_token, header_name="X-Admin-Token"):
                    self._send(403, {"error": "admin token required"})
                    return
                write_audit_line(self.config.audit_path, self.client_address[0], session_id, self.headers.get("X-Admin-Token", ""))
                self._send(200, row["raw_json"])
                return
            self._send(200, row["summary_json"])
            return
        if parsed.path == "/api/v1/parsed":
            kind = parse_qs(parsed.query).get("kind", [None])[0]
            self._send(200, {"items": self.storage.list_parsed(kind)})
            return
        if parsed.path == "/api/v1/audit":
            if not self.config.admin_token:
                self._send(503, {"error": "Admin token not configured"})
                return
            if not self._authorized(self.config.admin_token, header_name="X-Admin-Token"):
                self._send(403, {"error": "admin token required"})
                return
            self._send(200, {"lines": tail_audit(self.config.audit_path)})
            return
        if parsed.path == "/parsed":
            items = self.storage.list_parsed(parse_qs(parsed.query).get("kind", [None])[0])
            rows = "".join(f"<li>{item['session_id']} — {item['kind']}</li>" for item in items)
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(f"<html><body><h1>Parsed sessions</h1><ul>{rows}</ul></body></html>".encode("utf-8"))
            return
        self._send(404, {"error": "not found"})

    def _authorized(self, expected: str, header_name: str = "Authorization") -> bool:
        value = self.headers.get(header_name, "")
        if header_name == "Authorization":
            value = value.replace("Bearer ", "", 1)
        return expected and value == expected

    def _send(self, status: int, payload: dict) -> None:
        encoded = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


def create_server(host: str, port: int, config: ServerConfig) -> ThreadingHTTPServer:
    storage = Storage(config.db_path)

    class BoundHandler(UploadHandler):
        pass

    BoundHandler.config = config
    BoundHandler.storage = storage
    return ThreadingHTTPServer((host, port), BoundHandler)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default=8080, type=int)
    parser.add_argument("--db", default="./nfcgate.sqlite3")
    parser.add_argument("--api-token", default=os.environ.get("NFCGATE_API_TOKEN", "secret-token"))
    parser.add_argument("--admin-token", default=os.environ.get("NFCGATE_ADMIN_TOKEN"))
    parser.add_argument("--audit-path", default="audit.log")
    args = parser.parse_args()
    server = create_server(args.host, args.port, ServerConfig(args.db, args.api_token, args.admin_token, args.audit_path))
    server.serve_forever()
