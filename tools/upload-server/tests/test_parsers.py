import unittest

from nfcgate_server.parsers.emv import mask_pan, parse_emv
from nfcgate_server.parsers.mifare import parse_mifare
from nfcgate_server.parsers.pipeline import parse_session


class ParserTests(unittest.TestCase):
    def test_mask_pan(self):
        self.assertEqual(mask_pan("4761739001010010"), "476173******0010")

    def test_parse_session_unknown(self):
        result = parse_session({"frames": [{"data": "00FF"}]})
        self.assertEqual(result.kind, "unknown")

    def test_parse_mifare_detects_variant(self):
        result = parse_mifare([bytes.fromhex("44030804AABBCC")])
        self.assertEqual(result.variant, "Classic 1K")

    def test_parse_emv_extracts_aid(self):
        payload = bytes.fromhex("6F1A840E325041592E5359532E4444463031A5088801025F2D02656E")
        result = parse_emv([payload])
        self.assertEqual(result.aid, "325041592E5359532E4444463031")


if __name__ == "__main__":
    unittest.main()
