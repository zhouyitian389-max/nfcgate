from __future__ import annotations

import argparse
import json
import sqlite3

from nfcgate_server.models import ParsedRecord
from nfcgate_server.parsers.pipeline import PARSER_VERSION, parse_session
from nfcgate_server.storage import Storage


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", default="./nfcgate.sqlite3")
    parser.add_argument("--session-id")
    parser.add_argument("--all", action="store_true")
    args = parser.parse_args()
    storage = Storage(args.db)
    with sqlite3.connect(args.db) as connection:
        connection.row_factory = sqlite3.Row
        rows = connection.execute(
            "SELECT session_id, payload_json FROM sessions WHERE session_id = ?" if args.session_id else "SELECT session_id, payload_json FROM sessions",
            (args.session_id,) if args.session_id else (),
        ).fetchall()
    for row in rows:
        payload = json.loads(row["payload_json"])
        parsed = parse_session(payload)
        storage.save_parsed(ParsedRecord(row["session_id"], parsed.kind, parsed.summary_json, parsed.raw_json, parsed.warnings, PARSER_VERSION))


if __name__ == "__main__":
    main()
