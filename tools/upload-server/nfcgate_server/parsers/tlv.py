from __future__ import annotations


def parse_tlv(data: bytes) -> list[dict]:
    items = []
    index = 0
    while index < len(data):
        tag_start = data[index]
        index += 1
        tag = bytes([tag_start])
        if tag_start & 0x1F == 0x1F and index < len(data):
            tag += bytes([data[index]])
            index += 1
        if index >= len(data):
            break
        length = data[index]
        index += 1
        if length & 0x80:
            count = length & 0x7F
            if index + count > len(data):
                break
            length = int.from_bytes(data[index:index + count], "big")
            index += count
        value = data[index:index + length]
        index += length
        children = parse_tlv(value) if tag_start & 0x20 else []
        items.append({"tag_hex": tag.hex().upper(), "value": value, "children": children})
    return items
