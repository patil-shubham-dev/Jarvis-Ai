"""End-to-end test: WebSocket streaming flow.

Requires the backend server running at ws://localhost:8000.

Usage:
    pytest tests/e2e_test_streaming.py -v
"""

import json
import pytest
import asyncio
import websockets

WS_URL = "ws://localhost:8000/ws/chat"
TIMEOUT = 15


@pytest.fixture
def event_loop():
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    yield loop
    loop.close()


class TestWebSocketStreaming:

    @pytest.mark.asyncio
    async def test_send_and_receive_stream(self):
        async with websockets.connect(WS_URL, ping_timeout=TIMEOUT) as ws:
            await ws.send(json.dumps({"text": "Say hello in one sentence."}))
            events = []
            while True:
                try:
                    raw = await asyncio.wait_for(ws.recv(), timeout=TIMEOUT)
                    data = json.loads(raw)
                    events.append(data)
                    if data.get("type") == "stream_end":
                        break
                except asyncio.TimeoutError:
                    pytest.fail("Timed out waiting for stream_end")

        types = [e["type"] for e in events]
        assert "stream_start" in types, f"Missing stream_start. Got: {types}"
        assert "stream_token" in types, f"Missing stream_token. Got: {types}"
        assert "stream_end" in types, f"Missing stream_end. Got: {types}"

        tokens = [e["content"] for e in events if e["type"] == "stream_token"]
        full = "".join(tokens)
        assert len(full) > 0, "Empty stream response"

    @pytest.mark.asyncio
    async def test_ping_pong(self):
        async with websockets.connect(WS_URL, ping_timeout=TIMEOUT) as ws:
            await ws.send(json.dumps({"action": "ping"}))
            raw = await asyncio.wait_for(ws.recv(), timeout=TIMEOUT)
            data = json.loads(raw)
            assert data["type"] == "pong", f"Expected pong, got {data}"

    @pytest.mark.asyncio
    async def test_user_message_event(self):
        async with websockets.connect(WS_URL, ping_timeout=TIMEOUT) as ws:
            await ws.send(json.dumps({"text": "Hi"}))
            got_user_msg = False
            while True:
                try:
                    raw = await asyncio.wait_for(ws.recv(), timeout=TIMEOUT)
                    data = json.loads(raw)
                    if data.get("type") == "user_message":
                        got_user_msg = True
                        assert "content" in data and "timestamp" in data
                        break
                except asyncio.TimeoutError:
                    pytest.fail("Timed out waiting for user_message")
        assert got_user_msg, "Did not receive user_message event"

    @pytest.mark.asyncio
    async def test_empty_text_ignored(self):
        async with websockets.connect(WS_URL, ping_timeout=TIMEOUT) as ws:
            await ws.send(json.dumps({"text": ""}))
            await ws.send(json.dumps({"text": "Hi"}))
            events = []
            while True:
                try:
                    raw = await asyncio.wait_for(ws.recv(), timeout=TIMEOUT)
                    data = json.loads(raw)
                    events.append(data)
                    if data.get("type") == "stream_end":
                        break
                except asyncio.TimeoutError:
                    pytest.fail("Timed out waiting for stream_end")
        assert len(events) > 0, "Server did not respond after empty text + valid message"

    @pytest.mark.asyncio
    async def test_invalid_json_returns_error(self):
        async with websockets.connect(WS_URL, ping_timeout=TIMEOUT) as ws:
            await ws.send("not valid json")
            raw = await asyncio.wait_for(ws.recv(), timeout=TIMEOUT)
            data = json.loads(raw)
            assert data["type"] == "error", f"Expected error, got {data}"

    @pytest.mark.asyncio
    async def test_oversized_message_rejected(self):
        async with websockets.connect(WS_URL, ping_timeout=TIMEOUT) as ws:
            await ws.send(json.dumps({"text": "x" * 70000}))
            raw = await asyncio.wait_for(ws.recv(), timeout=TIMEOUT)
            data = json.loads(raw)
            assert data["type"] == "error", f"Expected error for oversized msg, got {data}"


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
