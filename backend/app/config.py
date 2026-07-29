import os
from typing import Dict, List
from dataclasses import dataclass, field
from dotenv import load_dotenv

load_dotenv()

@dataclass
class ProviderConfig:
    name: str
    api_key_env: str
    default_model: str
    fallback_model: str

@dataclass
class ModelConfig:
    openai: str = "gpt-4o-mini"
    openai_vision: str = "gpt-4o"
    anthropic: str = "claude-3-haiku-20240307"
    anthropic_vision: str = "claude-3-opus-20240229"
    google: str = "gemini-1.5-flash"
    google_vision: str = "gemini-1.5-pro"
    ollama: str = "llama3"
    embedding: str = "text-embedding-3-small"

@dataclass
class MemoryConfig:
    vector_dim: int = 1536
    top_k_recent: int = 3
    top_k_semantic: int = 5
    top_k_episodic: int = 3
    consolidation_interval_minutes: int = 10
    chroma_persist_dir: str = "./chroma_db"

@dataclass
class AppConfig:
    app_name: str = "Jarvis AI OS Backend"
    debug: bool = False
    cors_origins: List[str] = field(default_factory=lambda: os.getenv("CORS_ORIGINS", "http://localhost:3000,http://localhost:8000,http://127.0.0.1:3000,http://127.0.0.1:8000").split(","))
    ws_ping_interval: int = 30
    max_history: int = 50
    log_level: str = os.getenv("LOG_LEVEL", "INFO")

    models: ModelConfig = field(default_factory=ModelConfig)
    memory: MemoryConfig = field(default_factory=MemoryConfig)

    ALLOWED_PROXY_DOMAINS: List[str] = field(default_factory=lambda: [
        "api.openai.com",
        "api.anthropic.com",
        "generativelanguage.googleapis.com",
        "localhost:11434",
        "127.0.0.1:11434",
        "api.nvcf.nvidia.com",
    ])

    @property
    def providers(self) -> Dict[str, ProviderConfig]:
        return {
            "openai": ProviderConfig("openai", "OPENAI_API_KEY", self.models.openai, self.models.openai_vision),
            "anthropic": ProviderConfig("anthropic", "ANTHROPIC_API_KEY", self.models.anthropic, self.models.anthropic_vision),
            "google": ProviderConfig("google", "GOOGLE_API_KEY", self.models.google, self.models.google_vision),
            "ollama": ProviderConfig("ollama", "", self.models.ollama, self.models.ollama),
        }

    @property
    def active_provider(self) -> str:
        if not hasattr(self, '_cached_provider'):
            for name, cfg in self.providers.items():
                if name != "ollama" and os.getenv(cfg.api_key_env):
                    self._cached_provider = name
                    return self._cached_provider
            try:
                import httpx
                resp = httpx.get("http://localhost:11434/api/tags", timeout=2.0)
                if resp.status_code == 200:
                    self._cached_provider = "ollama"
                    return "ollama"
            except Exception:
                pass
            self._cached_provider = "openai"
        return self._cached_provider

    def get_api_key(self) -> str:
        provider = self.active_provider
        cfg = self.providers.get(provider)
        if cfg and cfg.api_key_env:
            return os.getenv(cfg.api_key_env, "")
        return os.getenv("OPENAI_API_KEY", "")

    def get_provider_api_key(self, provider: str) -> str:
        cfg = self.providers.get(provider)
        if not cfg:
            return ""
        return os.getenv(cfg.api_key_env, "")

    def is_allowed_proxy_url(self, url: str) -> bool:
        from urllib.parse import urlparse
        try:
            parsed = urlparse(url)
            host = parsed.hostname
            port = parsed.port
            netloc = f"{host}:{port}" if port else host
            return any(d in netloc for d in self.ALLOWED_PROXY_DOMAINS)
        except Exception:
            return False

config = AppConfig()
