from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any

from .tlv import parse_tlv

AID_BRANDS = {
    "A0000000031010": "visa",
    "A0000000041010": "mastercard",
    "A00000002501": "amex",
    "A0000000651010": "jcb",
    "A000000333010101": "unionpay",
    "325041592E5359532E4444463031": "ppse",
}


@dataclass
class EmvResult:
    brand: str | None
    aid: str | None
    pan_masked: str | None
    pan_raw: str | None
    expiry: str | None
    cardholder: str | None
    track2_masked: str | None
    track2_raw: str | None
    aip: str | None
    afl: str | None
    atc: str | None
    cryptogram: str | None
    extras: dict[str, Any]

    def to_summary(self) -> dict[str, Any]:
        data = asdict(self)
        data.pop("pan_raw", None)
        data.pop("track2_raw", None)
        return data


def mask_pan(value: str | None) -> str | None:
    if not value or len(value) < 10:
        return value
    return value[:6] + ("*" * (len(value) - 10)) + value[-4:]


def parse_emv(payloads: list[bytes]) -> EmvResult | None:
    tags: dict[str, bytes] = {}
    for payload in payloads:
        for item in parse_tlv(payload):
            flatten(item, tags)
    if not tags:
        return None
    aid = as_hex(tags.get("4F") or tags.get("84"))
    brand = AID_BRANDS.get(aid)
    pan = as_hex(tags.get("5A"))
    track2 = as_hex(tags.get("57"))
    return EmvResult(
        brand=brand,
        aid=aid,
        pan_masked=mask_pan(pan),
        pan_raw=pan,
        expiry=as_hex(tags.get("5F24")),
        cardholder=decode_text(tags.get("5F20")),
        track2_masked=mask_pan(track2),
        track2_raw=track2,
        aip=as_hex(tags.get("82")),
        afl=as_hex(tags.get("94")),
        atc=as_hex(tags.get("9F36")),
        cryptogram=as_hex(tags.get("9F26")),
        extras={key: as_hex(value) for key, value in tags.items() if key not in {"4F", "84", "5A", "5F24", "5F20", "57", "82", "94", "9F36", "9F26"}},
    )


def flatten(item: dict, tags: dict[str, bytes]) -> None:
    tags[item["tag_hex"]] = item["value"]
    for child in item.get("children", []):
        flatten(child, tags)


def as_hex(value: bytes | None) -> str | None:
    return value.hex().upper() if value else None


def decode_text(value: bytes | None) -> str | None:
    if not value:
        return None
    try:
        return value.decode("utf-8").strip()
    except UnicodeDecodeError:
        return value.decode("latin-1", errors="ignore").strip()
