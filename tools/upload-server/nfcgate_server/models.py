from dataclasses import dataclass, field
from typing import Any, Dict, List


@dataclass
class SessionRecord:
    session_id: str
    payload: Dict[str, Any]


@dataclass
class ParsedRecord:
    session_id: str
    kind: str
    summary_json: Dict[str, Any]
    raw_json: Dict[str, Any]
    warnings: List[str] = field(default_factory=list)
    parser_version: str = "0.1.0"
