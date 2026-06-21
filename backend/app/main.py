import os
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, field_validator
import logging
import json
import uuid
from datetime import datetime
from typing import Dict, Any, List, Optional

from app.config import config
from app.agents.orchestrator import AgentOrchestrator
from app.database.vector_db import VectorDB

logging.basicConfig(
    level=getattr(logging, config.log_level.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)

MAX_REQUEST_BODY = 1_048_576  # 1MB
MAX_WS_MESSAGE_SIZE = 65536   # 64KB

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

db = VectorDB(persist_directory=config.memory.chroma_persist_dir)
orchestrator = AgentOrchestrator(db)

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
            "messages_count": 0
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
        _shared_http_client = httpx.AsyncClient(timeout=60.0, limits=httpx.Limits(max_keepalive_connections=20, max_connections=100))
    return _shared_http_client

app = FastAPI(title=config.app_name, version="4.0.0")

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

@app.get("/")
async def root():
    return {
        "name": config.app_name,
        "version": "4.0.0",
        "status": "running",
        "connections": len(manager.active_connections),
        "tools": ["web_search", "code_exec", "android_bridge"]
    }

@app.get("/api/health")
async def health():
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "active_connections": len(manager.active_connections)
    }

@app.get("/api/sessions")
async def list_sessions():
    return {
        "sessions": [
            {"id": sid, "connected_at": info["connected_at"],
             "messages_count": info["messages_count"]}
            for sid, info in manager.sessions.items()
        ]
    }

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
                    "module": "chat",
                    "importance": float(meta.get("importance", 0.7))
                })
        return {"memories": memories}
    except Exception:
        logger.exception("Memory fetch error")
        return {"memories": []}

@app.post("/api/proxy/chat")
async def proxy_chat_completion(request: ChatProxyRequest):
    import httpx
    api_key = request.api_key or config.get_api_key()
    if not api_key:
        return {"error": "No API key provided"}

    messages = request.messages
    model = request.model or config.models.openai
    base_url = (request.base_url or "https://api.openai.com/v1/").rstrip("/")
    url = f"{base_url}/chat/completions"

    if not config.is_allowed_proxy_url(url):
        return {"error": "Proxy domain not allowed"}

    client = get_http_client()
    try:
        resp = await client.post(
            url,
            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            json={"model": model, "messages": messages}
        )
        return resp.json()
    except Exception as e:
        logger.exception("Proxy chat request failed")
        return {"error": f"Proxy request failed: {e}"}

@app.post("/api/proxy/embeddings")
async def proxy_embeddings(request: EmbeddingProxyRequest):
    import httpx
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
            json={
                "model": request.model or config.models.embedding,
                "input": request.input
            }
        )
        return resp.json()
    except Exception as e:
        logger.exception("Proxy embeddings request failed")
        return {"error": f"Proxy request failed: {e}"}

async def _handle_websocket(websocket: WebSocket, track_user_message: bool = True):
    session_id = await manager.connect(websocket)

    async def on_event(event: dict):
        await manager.send(session_id, event)

    try:
        while True:
            raw = await websocket.receive_text()
            if len(raw) > MAX_WS_MESSAGE_SIZE:
                await manager.send(session_id, {
                    "type": "error",
                    "content": "Message too large"
                })
                continue

            try:
                data = WSMessage(**json.loads(raw))
            except Exception as e:
                await manager.send(session_id, {
                    "type": "error",
                    "content": "Invalid message format"
                })
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
                    "timestamp": datetime.now().isoformat()
                })

            await manager.send(session_id, {
                "type": "stream_start",
                "content": "",
                "timestamp": datetime.now().isoformat()
            })

            response = await orchestrator.process_message_stream(
                session_id=session_id,
                text=data.text,
                on_event=on_event
            )

            chunk_size = 3
            for i in range(0, len(response), chunk_size):
                chunk = response[i:i + chunk_size]
                await manager.send(session_id, {
                    "type": "stream_token",
                    "content": chunk,
                    "done": False
                })
                await asyncio.sleep(0.008)

            await manager.send(session_id, {
                "type": "stream_end",
                "content": "",
                "done": True,
                "timestamp": datetime.now().isoformat()
            })

    except WebSocketDisconnect:
        logger.info(f"Client disconnected: {session_id}")
    except Exception:
        logger.exception(f"WebSocket error [{session_id}]")
        try:
            await manager.send(session_id, {
                "type": "error",
                "content": "An internal error occurred. Please try again."
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
    logger.info(f"Starting {config.app_name}")
    logger.info(f"Active provider: {config.active_provider}")
    logger.info("Tools registered:")
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
        log_level=config.log_level.lower()
    )
