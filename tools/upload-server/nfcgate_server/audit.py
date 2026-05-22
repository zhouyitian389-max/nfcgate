from __future__ import annotations

from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path


def write_audit_line(path: Path, ip: str, session_id: str, token: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    token_hash = sha256(token.encode("utf-8")).hexdigest()[:8]
    line = f"{datetime.now(timezone.utc).isoformat()} ip={ip} session={session_id} token={token_hash}\n"
    path.write_text((path.read_text() if path.exists() else "") + line, encoding="utf-8")


def tail_audit(path: Path, limit: int = 200) -> list[str]:
    if not path.exists():
        return []
    lines = path.read_text(encoding="utf-8").splitlines()
    return lines[-limit:]
