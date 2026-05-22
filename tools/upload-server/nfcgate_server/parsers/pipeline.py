from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .emv import parse_emv
from .mifare import parse_mifare


@dataclass
class ParsedSession:
    kind: str
    summary_json: dict[str, Any]
    raw_json: dict[str, Any]
    warnings: list[str]
    raw_frame_count: int


PARSER_VERSION = "0.1.0"


def parse_session(session_json: dict[str, Any]) -> ParsedSession:
    warnings: list[str] = []
    frames = session_json.get("frames", [])
    payloads = []
    for frame in frames:
        data = frame.get("data", "")
        try:
            payloads.append(bytes.fromhex(data))
        except ValueError:
            warnings.append("invalid hex frame")
    emv = parse_emv(payloads)
    if emv and emv.aid:
        return ParsedSession("emv", emv.to_summary(), emv.__dict__, warnings, len(frames))
    mifare = parse_mifare(payloads)
    if mifare and mifare.variant:
        return ParsedSession("mifare", mifare.to_summary(), mifare.__dict__, warnings, len(frames))
    return ParsedSession("unknown", {"kind": "unknown"}, {"kind": "unknown"}, warnings, len(frames))
