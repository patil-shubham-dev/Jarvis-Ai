import os
import asyncio
import json
import uuid
import logging
from datetime import datetime
from typing import Dict, Any, List, Optional

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, field_validator

from app.config import config
from app.agents.orchestrator import AgentOrchestrator
from app.database.vector_db import VectorDB
from app.providers import detect_provider, fetch_models, get_provider_config, PROVIDER_CONFIGS

logging.basicConfig(
    level=getattr(logging, config.log_level.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)

MAX_REQUEST_BODY = 1_048_576
MAX_WS_MESSAGE_SIZE = 65536

# ─── Request Models ────────────────────────────────────────────────

class DetectProviderRequest(BaseModel):
    api_key: str = Field(..., min_length=1, max_length=256)

class ListModelsRequest(BaseModel):
    api_key: str = Field(..., min_length=1, max_length=256)
    provider: Optional[str] = Field(default=None, max_length=64)

class ChatProxyRequest(BaseModel):
    api_key: Optional[str] = None
    messages: List[Dict[str, Any]] = Field(default_factory=list)
    model: Optional[str] = None
    base_url: Optional[str] = None

    @field_validator("messages")
    @classmethod
    def validate_messages(cls, v):
        if len(json.dumps(v)) > MAX_REQUEST_BODY:
            raise ValueError("Messages payload too large")
        if len(v) > 200:
            raise ValueError("Too many messages")
        return v

    @field_validator("base_url")
    @classmethod
    def validate_base_url(cls, v):
        if v and not config.is_allowed_proxy_url(v):
            raise ValueError(f"Proxy domain not allowed: {v}")
        return v

class EmbeddingProxyRequest(BaseModel):
    api_key: Optional[str] = None
    model: Optional[str] = None
    input: str = Field(default="", max_length=32768)
    base_url: Optional[str] = None

    @field_validator("base_url")
    @classmethod
    def validate_base_url(cls, v):
        if v and not config.is_allowed_proxy_url(v):
            raise ValueError(f"Proxy domain not allowed: {v}")
        return v

class WSMessage(BaseModel):
    text: str = Field(default="", max_length=MAX_WS_MESSAGE_SIZE)
    action: str = Field(default="", max_length=64)
    api_key: Optional[str] = Field(default=None, max_length=1024)
    model: Optional[str] = Field(default=None, max_length=128)

# ─── Database & Orchestrator ──────────────────────────────────────

db = VectorDB(persist_directory=config.memory.chroma_persist_dir)
orchestrator = AgentOrchestrator(db)

# ─── Connection Manager ────────────────────────────────────────────

class ConnectionManager:
    def __init__(self):
        self.active_connections: Dict[str, WebSocket] = {}
        self.sessions: Dict[str, Dict] = {}

    async def connect(self, websocket: WebSocket) -> str:
        await websocket.accept()
        session_id = str(uuid.uuid4())
        self.active_connections[session_id] = websocket
        self.sessions[session_id] = {
            "id": session_id,
            "connected_at": datetime.now().isoformat(),
            "messages_count": 0,
        }
        logger.info(f"WebSocket connected: {session_id}")
        return session_id

    async def disconnect(self, session_id: str):
        self.active_connections.pop(session_id, None)
        self.sessions.pop(session_id, None)
        logger.info(f"WebSocket disconnected: {session_id}")

    async def send(self, session_id: str, data: dict):
        ws = self.active_connections.get(session_id)
        if ws:
            try:
                await ws.send_text(json.dumps(data, default=str))
            except Exception:
                logger.exception(f"Send error [{session_id}]")
                await self.disconnect(session_id)

    async def broadcast(self, data: dict):
        for session_id in list(self.active_connections.keys()):
            await self.send(session_id, data)

manager = ConnectionManager()
_shared_http_client = None

def get_http_client():
    global _shared_http_client
    if _shared_http_client is None:
        import httpx
        _shared_http_client = httpx.AsyncClient(
            timeout=120.0,
            limits=httpx.Limits(max_keepalive_connections=20, max_connections=100)
        )
    return _shared_http_client

# ─── FastAPI App ───────────────────────────────────────────────────

app = FastAPI(title=config.app_name, version="4.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=config.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.middleware("http")
async def limit_body_size(request: Request, call_next):
    content_length = request.headers.get("content-length")
    if content_length and int(content_length) > MAX_REQUEST_BODY:
        return JSONResponse(status_code=413, content={"error": "Request body too large"})
    return await call_next(request)

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.exception(f"Unhandled error on {request.method} {request.url.path}")
    return JSONResponse(status_code=500, content={"error": "Internal server error"})

# ─── Provider Discovery API ────────────────────────────────────────

@app.get("/")
async def root():
    return {
        "name": config.app_name,
        "version": "4.1.0",
        "status": "running",
        "connections": len(manager.active_connections),
        "supported_providers": list(PROVIDER_CONFIGS.keys()),
    }

@app.get("/api/health")
async def health():
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "active_connections": len(manager.active_connections),
    }

@app.post("/api/providers/detect")
async def detect_provider_endpoint(req: DetectProviderRequest):
    provider = detect_provider(req.api_key)
    if not provider:
        return {"provider": None, "error": "Could not detect provider from API key"}
    info = get_provider_config(provider)
    return {"provider": provider, "name": info["name"] if info else provider}

@app.post("/api/providers/models")
async def list_models_endpoint(req: ListModelsRequest):
    provider = req.provider or detect_provider(req.api_key)
    if not provider:
        return {"models": [], "error": "Could not detect provider"}
    models = await fetch_models(req.api_key, provider)
    return {"provider": provider, "models": models}

@app.get("/api/providers")
async def list_providers():
    return {
        "providers": [
            {"id": pid, "name": info["name"]}
            for pid, info in PROVIDER_CONFIGS.items()
        ]
    }

# ─── Memory API ────────────────────────────────────────────────────

@app.get("/api/memories")
async def list_memories(query: str = "", limit: int = 50):
    limit = min(max(limit, 1), 500)
    try:
        if query:
            results = await db.search_conversations_async(query, n_results=limit)
        else:
            results = db.conversations.get(limit=limit)
        memories = []
        if results and results.get("ids") and results["ids"]:
            for i in range(len(results["ids"][0])):
                meta = results.get("metadatas", [{}])[0][i] if results.get("metadatas") else {}
                memories.append({
                    "id": results["ids"][0][i],
                    "text": results.get("documents", [[]])[0][i] if results.get("documents") else "",
                    "timestamp": meta.get("timestamp", ""),
                    "category": meta.get("category", "conversation"),
                    "module": meta.get("module", "chat"),
                    "importance": float(meta.get("importance", 0.7)),
                })
        return {"memories": memories}
    except Exception:
        logger.exception("Memory fetch error")
        return {"memories": []}

@app.delete("/api/memories")
async def clear_memories():
    try:
        db.conversations.delete(ids=db.conversations.get()["ids"])
        db.documents.delete(ids=db.documents.get()["ids"])
        return {"status": "cleared"}
    except Exception:
        logger.exception("Memory clear error")
        return {"error": "Failed to clear memories"}

# ─── Proxy API ─────────────────────────────────────────────────────

@app.post("/api/proxy/test")
async def proxy_test_connection(req: DetectProviderRequest):
    """Dedicated lightweight endpoint for connection testing.
    Sends a minimal ping to the provider and returns a clear result."""
    api_key = req.api_key
    if not api_key:
        return {"success": False, "error": "No API key provided"}

    provider = detect_provider(api_key)
    if not provider:
        return {"success": False, "error": "Could not detect provider from API key format"}

    provider_config = get_provider_config(provider)
    if not provider_config:
        return {"success": False, "error": f"Unknown provider: {provider}"}

    test_messages = [{"role": "user", "content": "Reply with one word: OK"}]
    model = provider_config.get("default_model", "gpt-4o-mini")

    try:
        if provider == "anthropic":
            result = await _proxy_anthropic_chat(api_key, model, test_messages)
        elif provider == "google":
            result = await _proxy_google_chat(api_key, model, test_messages)
        else:
            result = await _proxy_openai_chat(api_key, model, test_messages)

        if "error" in result:
            return {"success": False, "error": result["error"]}

        # Extract response content based on provider format
        content = (
            result.get("choices", [{}])[0].get("message", {}).get("content", "")
            or result.get("content", [{}])[0].get("text", "")
            or "OK"
        )
        return {"success": True, "provider": provider, "model": model, "response": content}
    except Exception as e:
        logger.exception("Connection test failed")
        return {"success": False, "error": f"Connection failed: {str(e)}"}

@app.post("/api/proxy/chat")
async def proxy_chat_completion(request: ChatProxyRequest):
    api_key = request.api_key or config.get_api_key()
    if not api_key:
        return {"error": "No API key provided"}

    provider = detect_provider(api_key)
    provider_config = get_provider_config(provider) if provider else None

    messages = request.messages
    model = request.model or (provider_config["default_model"] if provider_config else "gpt-4o-mini")

    if provider == "anthropic":
        return await _proxy_anthropic_chat(api_key, model, messages)
    elif provider == "google":
        return await _proxy_google_chat(api_key, model, messages)
    else:
        return await _proxy_openai_chat(api_key, model, messages, request.base_url)

async def _proxy_openai_chat(api_key: str, model: str, messages: list, base_url: str = None):
    base_url = (base_url or "https://api.openai.com/v1/").rstrip("/")
    url = f"{base_url}/chat/completions"
    if not config.is_allowed_proxy_url(url):
        return {"error": "Proxy domain not allowed"}
    client = get_http_client()
    try:
        resp = await client.post(
            url,
            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            json={"model": model, "messages": messages},
        )
        return resp.json()
    except Exception as e:
        logger.exception("Proxy chat request failed")
        return {"error": f"Proxy request failed: {e}"}

async def _proxy_anthropic_chat(api_key: str, model: str, messages: list):
    url = "https://api.anthropic.com/v1/messages"
    system = None
    filtered = []
    for m in messages:
        if m.get("role") == "system":
            system = m["content"]
        else:
            filtered.append(m)
    body = {"model": model, "max_tokens": 4096, "messages": filtered}
    if system:
        body["system"] = system
    client = get_http_client()
    try:
        resp = await client.post(
            url,
            headers={
                "x-api-key": api_key,
                "anthropic-version": "2023-06-01",
                "Content-Type": "application/json",
            },
            json=body,
        )
        return resp.json()
    except Exception as e:
        logger.exception("Anthropic proxy failed")
        return {"error": f"Proxy request failed: {e}"}

async def _proxy_google_chat(api_key: str, model: str, messages: list):
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    contents = []
    for m in messages:
        if m.get("role") in ("user", "assistant"):
            contents.append({"role": m["role"], "parts": [{"text": m["content"]}]})
    client = get_http_client()
    try:
        resp = await client.post(
            url,
            headers={"x-goog-api-key": api_key, "Content-Type": "application/json"},
            json={"contents": contents},
        )
        return resp.json()
    except Exception as e:
        logger.exception("Google proxy failed")
        return {"error": f"Proxy request failed: {e}"}

@app.post("/api/proxy/embeddings")
async def proxy_embeddings(request: EmbeddingProxyRequest):
    api_key = request.api_key or config.get_api_key()
    if not api_key:
        return {"error": "No API key provided."}
    base_url = (request.base_url or "https://api.openai.com/v1/").rstrip("/")
    url = f"{base_url}/embeddings"
    if not config.is_allowed_proxy_url(url):
        return {"error": "Proxy domain not allowed"}
    client = get_http_client()
    try:
        resp = await client.post(
            url,
            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            json={"model": request.model or config.models.embedding, "input": request.input},
        )
        return resp.json()
    except Exception as e:
        logger.exception("Proxy embeddings request failed")
        return {"error": f"Proxy request failed: {e}"}

# ─── WebSocket with True Streaming ─────────────────────────────────

async def _stream_provider_response(api_key: str, model: str, messages: list, on_token):
    provider = detect_provider(api_key)
    provider_config = get_provider_config(provider) if provider else None
    if provider == "anthropic":
        await _stream_anthropic(api_key, model, messages, on_token)
    elif provider == "google":
        await _stream_google(api_key, model, messages, on_token)
    else:
        base_url = (provider_config["base_url"] if provider_config else "https://api.openai.com/v1").rstrip("/")
        await _stream_openai(api_key, model, messages, on_token, base_url)

async def _stream_openai(api_key: str, model: str, messages: list, on_token, base_url: str = "https://api.openai.com/v1"):
    provider = detect_provider(api_key)
    provider_config = get_provider_config(provider) if provider else None
    if provider_config:
        api_header = provider_config["api_key_header"]
        api_prefix = provider_config["api_key_prefix"]
    else:
        api_header = "Authorization"
        api_prefix = "Bearer "
    url = f"{base_url}/chat/completions"
    async with httpx.AsyncClient(timeout=120.0) as client:
        async with client.stream(
            "POST",
            url,
            headers={api_header: f"{api_prefix}{api_key}", "Content-Type": "application/json"},
            json={"model": model, "messages": messages, "stream": True},
        ) as resp:
            if resp.status_code != 200:
                error_text = await resp.aread()
                raise RuntimeError(f"API returned status {resp.status_code}: {error_text.decode(errors='replace')[:500]}")
            async for line in resp.aiter_lines():
                if not line.startswith("data: "):
                    continue
                payload = line[6:].strip()
                if payload == "[DONE]":
                    break
                try:
                    chunk = json.loads(payload)
                    delta = chunk.get("choices", [{}])[0].get("delta", {})
                    content = delta.get("content", "")
                    if content:
                        await on_token(content)
                except json.JSONDecodeError:
                    continue

async def _stream_anthropic(api_key: str, model: str, messages: list, on_token):
    url = "https://api.anthropic.com/v1/messages"
    system = None
    filtered = []
    for m in messages:
        if m.get("role") == "system":
            system = m["content"]
        else:
            filtered.append(m)
    body = {"model": model, "max_tokens": 4096, "messages": filtered, "stream": True}
    if system:
        body["system"] = system
    async with httpx.AsyncClient(timeout=120.0) as client:
        async with client.stream(
            "POST",
            url,
            headers={
                "x-api-key": api_key,
                "anthropic-version": "2023-06-01",
                "Content-Type": "application/json",
            },
            json=body,
        ) as resp:
            if resp.status_code != 200:
                error_text = await resp.aread()
                raise RuntimeError(f"Anthropic API returned status {resp.status_code}: {error_text.decode(errors='replace')[:500]}")
            async for line in resp.aiter_lines():
                if not line.startswith("data: "):
                    continue
                payload = line[6:].strip()
                try:
                    chunk = json.loads(payload)
                    if chunk.get("type") == "content_block_delta":
                        delta = chunk.get("delta", {})
                        text = delta.get("text", "")
                        if text:
                            await on_token(text)
                except json.JSONDecodeError:
                    continue

async def _stream_google(api_key: str, model: str, messages: list, on_token):
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent"
    contents = []
    for m in messages:
        if m.get("role") in ("user", "assistant"):
            contents.append({"role": m["role"], "parts": [{"text": m["content"]}]})
    async with httpx.AsyncClient(timeout=120.0) as client:
        async with client.stream(
            "POST",
            url,
            headers={"x-goog-api-key": api_key, "Content-Type": "application/json"},
            json={"contents": contents},
        ) as resp:
            if resp.status_code != 200:
                error_text = await resp.aread()
                raise RuntimeError(f"Google API returned status {resp.status_code}: {error_text.decode(errors='replace')[:500]}")
            async for line in resp.aiter_lines():
                if not line:
                    continue
                try:
                    chunk = json.loads(line)
                    candidates = chunk.get("candidates", [])
                    if candidates:
                        parts = candidates[0].get("content", {}).get("parts", [])
                        for part in parts:
                            text = part.get("text", "")
                            if text:
                                await on_token(text)
                except json.JSONDecodeError:
                    continue

async def _handle_websocket(websocket: WebSocket, track_user_message: bool = True):
    session_id = await manager.connect(websocket)

    async def on_event(event: dict):
        await manager.send(session_id, event)

    try:
        while True:
            raw = await websocket.receive_text()
            if len(raw) > MAX_WS_MESSAGE_SIZE:
                await manager.send(session_id, {"type": "error", "content": "Message too large"})
                continue

            try:
                data = WSMessage(**json.loads(raw))
            except Exception:
                await manager.send(session_id, {"type": "error", "content": "Invalid message format"})
                continue

            if data.action == "ping":
                await manager.send(session_id, {"type": "pong"})
                continue

            if not data.text.strip():
                continue

            if track_user_message:
                session = manager.sessions.get(session_id, {})
                session["messages_count"] = session.get("messages_count", 0) + 1
                await manager.send(session_id, {
                    "type": "user_message",
                    "content": data.text,
                    "timestamp": datetime.now().isoformat(),
                })

            await manager.send(session_id, {
                "type": "stream_start",
                "content": "",
                "timestamp": datetime.now().isoformat(),
            })

            api_key = data.api_key or config.get_api_key()
            model = data.model or config.models.openai

            if api_key:
                provider = detect_provider(api_key)
                provider_config = get_provider_config(provider) if provider else None
                if provider and provider_config:
                    model = data.model or provider_config["default_model"]
                elif provider == "anthropic":
                    model = data.model or "claude-sonnet-4-20250514"
                elif provider == "google":
                    model = data.model or "gemini-2.0-flash"

                history = []
                history.append({"role": "system", "content": "You are Jarvis, a premium AI OS assistant. Be concise and helpful."})
                history.append({"role": "user", "content": data.text})

                token_count = 0
                async def on_token(content: str):
                    nonlocal token_count
                    token_count += 1
                    await manager.send(session_id, {
                        "type": "stream_token",
                        "content": content,
                        "done": False,
                        "index": token_count,
                    })

                try:
                    await _stream_provider_response(api_key, model, history, on_token)
                except Exception as e:
                    logger.exception("Streaming error")
                    await manager.send(session_id, {"type": "error", "content": f"Streaming failed: {str(e)}"})
                    # DO NOT return — let stream_end be sent below
            else:
                try:
                    response = await orchestrator.process_message_stream(
                        session_id=session_id,
                        text=data.text,
                        on_event=on_event,
                    )
                    if not response:
                        response = "I'm ready to help. What would you like me to do?"
                    chunk_size = 3
                    for i in range(0, len(response), chunk_size):
                        chunk = response[i:i + chunk_size]
                        await manager.send(session_id, {
                            "type": "stream_token",
                            "content": chunk,
                            "done": False,
                        })
                        await asyncio.sleep(0.008)
                except Exception as e:
                    logger.exception("Orchestrator processing failed")
                    await manager.send(session_id, {"type": "error", "content": f"Processing failed: {str(e)}"})

            await manager.send(session_id, {
                "type": "stream_end",
                "content": "",
                "done": True,
                "timestamp": datetime.now().isoformat(),
            })

    except WebSocketDisconnect:
        logger.info(f"Client disconnected: {session_id}")
    except Exception:
        logger.exception(f"WebSocket error [{session_id}]")
        try:
            await manager.send(session_id, {
                "type": "error",
                "content": "An internal error occurred. Please try again.",
            })
        except Exception:
            pass
    finally:
        await manager.disconnect(session_id)

@app.websocket("/ws/chat")
async def websocket_endpoint(websocket: WebSocket):
    await _handle_websocket(websocket, track_user_message=True)

@app.websocket("/ws/stream")
async def websocket_stream(websocket: WebSocket):
    await _handle_websocket(websocket, track_user_message=False)

@app.on_event("startup")
async def startup():
    logger.info(f"Starting {config.app_name} v4.1.0")
    logger.info("API key: BYOK (Bring Your Own Key) — keys sent from frontend")
    from app.tools import ToolRegistry
    for name, status in ToolRegistry().list_tools().items():
        logger.info(f"  - {name}: {status}")

@app.on_event("shutdown")
async def shutdown():
    global _shared_http_client
    logger.info("Shutting down...")
    await orchestrator.shutdown()
    if _shared_http_client:
        await _shared_http_client.aclose()
        _shared_http_client = None

if __name__ == "__main__":
    import uvicorn
    host = os.getenv("HOST", "127.0.0.1")
    port = int(os.getenv("PORT", "8000"))
    uvicorn.run(
        "app.main:app",
        host=host,
        port=port,
        reload=os.getenv("DEBUG", "false").lower() == "true",
        log_level=config.log_level.lower(),
    )
