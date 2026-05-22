from __future__ import annotations


def split_apdu(data: bytes) -> dict:
    if len(data) < 4:
        return {"kind": "short", "data": data.hex().upper()}
    cla, ins, p1, p2 = data[:4]
    kind = {
        (0x00, 0xA4): "select",
        (0x80, 0xA8): "gpo",
        (0x00, 0xB2): "read_record",
        (0x80, 0xCA): "get_data",
    }.get((cla, ins), "other")
    return {
        "kind": kind,
        "cla": cla,
        "ins": ins,
        "p1": p1,
        "p2": p2,
        "body": data[4:].hex().upper(),
    }
