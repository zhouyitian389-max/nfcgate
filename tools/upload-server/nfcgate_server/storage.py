from __future__ import annotations

import json
import sqlite3
from pathlib import Path

from .models import ParsedRecord, SessionRecord


class Storage:
    def __init__(self, db_path: str | Path) -> None:
        self.db_path = str(db_path)
        self._init_db()

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.db_path)
        connection.row_factory = sqlite3.Row
        return connection

    def _init_db(self) -> None:
        with self._connect() as connection:
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS sessions (
                    session_id TEXT PRIMARY KEY,
                    payload_json TEXT NOT NULL,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """
            )
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS parsed_sessions (
                    session_id TEXT PRIMARY KEY,
                    kind TEXT NOT NULL,
                    summary_json TEXT NOT NULL,
                    raw_json TEXT NOT NULL,
                    warnings_json TEXT NOT NULL,
                    parsed_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    parser_version TEXT NOT NULL
                )
                """
            )

    def save_session(self, record: SessionRecord) -> None:
        with self._connect() as connection:
            connection.execute(
                "INSERT OR REPLACE INTO sessions(session_id, payload_json) VALUES (?, ?)",
                (record.session_id, json.dumps(record.payload)),
            )

    def save_parsed(self, record: ParsedRecord) -> None:
        with self._connect() as connection:
            connection.execute(
                """
                INSERT OR REPLACE INTO parsed_sessions(session_id, kind, summary_json, raw_json, warnings_json, parser_version)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (
                    record.session_id,
                    record.kind,
                    json.dumps(record.summary_json),
                    json.dumps(record.raw_json),
                    json.dumps(record.warnings),
                    record.parser_version,
                ),
            )

    def get_parsed(self, session_id: str) -> dict | None:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT * FROM parsed_sessions WHERE session_id = ?",
                (session_id,),
            ).fetchone()
        if row is None:
            return None
        return {
            "session_id": row["session_id"],
            "kind": row["kind"],
            "summary_json": json.loads(row["summary_json"]),
            "raw_json": json.loads(row["raw_json"]),
            "warnings": json.loads(row["warnings_json"]),
            "parser_version": row["parser_version"],
        }

    def list_parsed(self, kind: str | None = None) -> list[dict]:
        query = "SELECT * FROM parsed_sessions"
        args: tuple = ()
        if kind:
            query += " WHERE kind = ?"
            args = (kind,)
        with self._connect() as connection:
            rows = connection.execute(query, args).fetchall()
        return [
            {
                "session_id": row["session_id"],
                "kind": row["kind"],
                "summary_json": json.loads(row["summary_json"]),
            }
            for row in rows
        ]
