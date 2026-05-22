from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any


@dataclass
class MifareResult:
    variant: str | None
    uid: str | None
    atqa: str | None
    sak: str | None
    sectors_or_pages: list[str]

    def to_summary(self) -> dict[str, Any]:
        return asdict(self)


def parse_mifare(frames: list[bytes]) -> MifareResult | None:
    if not frames:
        return None
    first = frames[0]
    uid = first[:7].hex().upper() if len(first) >= 4 else None
    atqa = first[:2].hex().upper() if len(first) >= 2 else None
    sak = first[2:3].hex().upper() if len(first) >= 3 else None
    variant = classify(atqa, sak, uid)
    pages = [frame.hex().upper() for frame in frames[1:] if frame]
    if not variant and not pages:
        return None
    return MifareResult(variant=variant, uid=uid, atqa=atqa, sak=sak, sectors_or_pages=pages)


def classify(atqa: str | None, sak: str | None, uid: str | None) -> str | None:
    if sak == "08":
        return "Classic 1K"
    if sak == "18":
        return "Classic 4K"
    if sak == "20" and atqa == "4403":
        return "DESFire"
    if sak == "00" and uid and uid.startswith("04"):
        return "Ultralight"
    return None
