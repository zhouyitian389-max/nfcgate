import argparse
import asyncio
import json
import logging
from typing import Optional

import websockets
from smartcard.Exceptions import NoCardException
from smartcard.System import readers


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")


def to_hex(data):
    return "".join(f"{b:02X}" for b in data)


def from_hex(data: str):
    cleaned = "".join(data.split())
    if len(cleaned) % 2:
        cleaned = "0" + cleaned
    return [int(cleaned[i:i + 2], 16) for i in range(0, len(cleaned), 2)]


class PcscBridge:
    def __init__(self):
        self.connection = None
        self.atr = None

    def connect(self):
        available = readers()
        if not available:
            raise RuntimeError("No PC/SC readers found")
        self.connection = available[0].createConnection()
        self.connection.connect()
        self.atr = self.connection.getATR()
        logging.info("Card connected, ATR=%s", to_hex(self.atr))

    def ensure_connected(self):
        if self.connection is None:
            self.connect()

    def transmit(self, apdu_hex: str) -> str:
        self.ensure_connected()
        try:
            response, sw1, sw2 = self.connection.transmit(from_hex(apdu_hex))
            return to_hex(response + [sw1, sw2])
        except NoCardException:
            logging.warning("Card removed")
            self.connection = None
            self.atr = None
            raise


async def relay_loop(url: str, token: str, session_id: str):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    bridge = PcscBridge()

    while True:
        try:
            async with websockets.connect(url, additional_headers=headers, ping_interval=20, ping_timeout=20) as ws:
                await ws.send(json.dumps({"type": "session_join", "sessionId": session_id, "role": "external"}))
                logging.info("Joined relay session %s", session_id)

                try:
                    bridge.ensure_connected()
                    if bridge.atr is not None:
                        await ws.send(json.dumps({"type": "session_paired", "sessionId": session_id, "atr": to_hex(bridge.atr)}))
                except Exception as exc:
                    logging.warning("No card at startup: %s", exc)

                async for raw in ws:
                    payload = json.loads(raw)
                    if payload.get("type") != "apdu_command":
                        continue
                    try:
                        response_hex = bridge.transmit(payload.get("data", ""))
                    except NoCardException:
                        response_hex = "6A82"
                    await ws.send(json.dumps({
                        "type": "apdu_response",
                        "sessionId": payload.get("sessionId", session_id),
                        "data": response_hex
                    }))
        except Exception as exc:
            logging.error("Relay connection failed: %s", exc)
            await asyncio.sleep(2)


def parse_args():
    parser = argparse.ArgumentParser(description="ACR39U relay bridge")
    parser.add_argument("--server", required=True, help="WebSocket relay endpoint, e.g. ws://localhost:8080/ws/relay")
    parser.add_argument("--session", required=True, help="Relay session token/session ID")
    parser.add_argument("--jwt", default="", help="JWT for Authorization header")
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    asyncio.run(relay_loop(args.server, args.jwt, args.session))
