import re
import httpx
from typing import Optional

PROVIDER_PATTERNS = {
    "openrouter": re.compile(r"^sk-or-v1-|^sk-or-"),
    "anthropic": re.compile(r"^sk-ant-"),
    "deepseek": re.compile(r"^sk-deepseek-"),
    "openai": re.compile(r"^sk-proj-|^sk-"),
    "google": re.compile(r"^AIza"),
    "groq": re.compile(r"^gsk_"),
    "mistral": re.compile(r"^[A-Za-z0-9]{32,}$"),
    "nvidia": re.compile(r"^nvapi-"),
}

PROVIDER_CONFIGS = {
    "openai": {
        "name": "OpenAI",
        "base_url": "https://api.openai.com/v1",
        "models_endpoint": "/models",
        "chat_endpoint": "/chat/completions",
        "embedding_endpoint": "/embeddings",
        "default_model": "gpt-4o-mini",
        "api_key_header": "Authorization",
        "api_key_prefix": "Bearer ",
    },
    "anthropic": {
        "name": "Anthropic",
        "base_url": "https://api.anthropic.com/v1",
        "models_endpoint": "/models",
        "chat_endpoint": "/messages",
        "embedding_endpoint": None,
        "default_model": "claude-sonnet-4-20250514",
        "api_key_header": "x-api-key",
        "api_key_prefix": "",
    },
    "google": {
        "name": "Google Gemini",
        "base_url": "https://generativelanguage.googleapis.com/v1beta",
        "models_endpoint": "/models",
        "chat_endpoint": "/models/{model}:generateContent",
        "chat_stream_endpoint": "/models/{model}:streamGenerateContent",
        "embedding_endpoint": "/models/{model}:embedContent",
        "default_model": "gemini-2.0-flash",
        "api_key_header": "x-goog-api-key",
        "api_key_prefix": "",
    },
    "groq": {
        "name": "Groq",
        "base_url": "https://api.groq.com/openai/v1",
        "models_endpoint": "/models",
        "chat_endpoint": "/chat/completions",
        "embedding_endpoint": None,
        "default_model": "llama-3.3-70b-versatile",
        "api_key_header": "Authorization",
        "api_key_prefix": "Bearer ",
    },
    "mistral": {
        "name": "Mistral",
        "base_url": "https://api.mistral.ai/v1",
        "models_endpoint": "/models",
        "chat_endpoint": "/chat/completions",
        "embedding_endpoint": "/embeddings",
        "default_model": "mistral-small-latest",
        "api_key_header": "Authorization",
        "api_key_prefix": "Bearer ",
    },
    "openrouter": {
        "name": "OpenRouter",
        "base_url": "https://openrouter.ai/api/v1",
        "models_endpoint": "/models",
        "chat_endpoint": "/chat/completions",
        "embedding_endpoint": None,
        "default_model": "openai/gpt-4o-mini",
        "api_key_header": "Authorization",
        "api_key_prefix": "Bearer ",
    },
    "deepseek": {
        "name": "DeepSeek",
        "base_url": "https://api.deepseek.com/v1",
        "models_endpoint": "/models",
        "chat_endpoint": "/chat/completions",
        "embedding_endpoint": None,
        "default_model": "deepseek-chat",
        "api_key_header": "Authorization",
        "api_key_prefix": "Bearer ",
    },
    "nvidia": {
        "name": "NVIDIA",
        "base_url": "https://api.nvcf.nvidia.com/v1",
        "models_endpoint": "/models",
        "chat_endpoint": "/chat/completions",
        "embedding_endpoint": None,
        "default_model": "meta/llama-3.1-70b-instruct",
        "api_key_header": "Authorization",
        "api_key_prefix": "Bearer ",
    },
}

def detect_provider(api_key: str) -> Optional[str]:
    if not api_key:
        return None
    for provider_id, pattern in PROVIDER_PATTERNS.items():
        if pattern.match(api_key):
            return provider_id
    if api_key.startswith("sk-"):
        return "openai"
    return None

def get_provider_config(provider_id: str) -> Optional[dict]:
    return PROVIDER_CONFIGS.get(provider_id)

async def fetch_models(api_key: str, provider_id: str) -> list[dict]:
    config = get_provider_config(provider_id)
    if not config:
        return []
    base_url = config["base_url"]
    endpoint = config["models_endpoint"]
    headers = {}
    if config["api_key_prefix"]:
        headers[config["api_key_header"]] = f"{config['api_key_prefix']}{api_key}"
    else:
        headers[config["api_key_header"]] = api_key
    url = f"{base_url}{endpoint}"
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.get(url, headers=headers)
            if resp.status_code != 200:
                return _fallback_models(provider_id)
            data = resp.json()
            return _parse_provider_models(provider_id, data)
    except Exception:
        return _fallback_models(provider_id)

def _parse_provider_models(provider_id: str, data: dict) -> list[dict]:
    if provider_id == "openai" or provider_id in ("groq", "mistral", "openrouter", "deepseek", "nvidia"):
        raw = data.get("data", [])
        chat_keywords = ["gpt", "o1", "o3", "chat", "claude", "gemini", "llama", "mixtral", "mistral", "deepseek"]
        blacklist = ["instruct", "realtime", "audio", "embedding", "moderation", "whisper", "tts", "dall-e"]
        models = []
        for m in raw:
            mid = m.get("id", "")
            if not any(k in mid.lower() for k in chat_keywords):
                continue
            if any(b in mid.lower() for b in blacklist):
                continue
            models.append({
                "id": mid,
                "name": m.get("id", ""),
                "provider": provider_id,
            })
        return sorted(models, key=lambda x: x["id"])
    elif provider_id == "anthropic":
        raw = data.get("data", [])
        return [
            {"id": m["id"], "name": m.get("display_name", m["id"]), "provider": provider_id}
            for m in raw
        ]
    elif provider_id == "google":
        raw = data.get("models", [])
        chat_models = []
        for m in raw:
            name = m.get("name", "")
            short_id = name.replace("models/", "", 1) if name.startswith("models/") else name
            methods = m.get("supportedGenerationMethods", [])
            if "generateContent" not in methods:
                continue
            if "embedding" in short_id.lower():
                continue
            chat_models.append({
                "id": short_id,
                "name": m.get("displayName", short_id),
                "provider": provider_id,
            })
        return sorted(chat_models, key=lambda x: x["id"])
    return []

def _fallback_models(provider_id: str) -> list[dict]:
    fallbacks = {
        "openai": [
            {"id": "gpt-4o", "name": "GPT-4o", "provider": "openai"},
            {"id": "gpt-4o-mini", "name": "GPT-4o Mini", "provider": "openai"},
            {"id": "gpt-4-turbo", "name": "GPT-4 Turbo", "provider": "openai"},
            {"id": "gpt-3.5-turbo", "name": "GPT-3.5 Turbo", "provider": "openai"},
            {"id": "o1-mini", "name": "O1 Mini", "provider": "openai"},
        ],
        "anthropic": [
            {"id": "claude-sonnet-4-20250514", "name": "Claude Sonnet 4", "provider": "anthropic"},
        ],
        "google": [
            {"id": "gemini-2.0-flash", "name": "Gemini 2.0 Flash", "provider": "google"},
            {"id": "gemini-2.5-pro", "name": "Gemini 2.5 Pro", "provider": "google"},
        ],
        "groq": [
            {"id": "llama-3.3-70b-versatile", "name": "Llama 3.3 70B", "provider": "groq"},
            {"id": "mixtral-8x7b-32768", "name": "Mixtral 8x7B", "provider": "groq"},
        ],
        "mistral": [
            {"id": "mistral-small-latest", "name": "Mistral Small", "provider": "mistral"},
            {"id": "mistral-medium-latest", "name": "Mistral Medium", "provider": "mistral"},
        ],
        "openrouter": [
            {"id": "openai/gpt-4o", "name": "OpenAI GPT-4o (via OR)", "provider": "openrouter"},
            {"id": "anthropic/claude-sonnet-4", "name": "Claude Sonnet 4 (via OR)", "provider": "openrouter"},
        ],
        "deepseek": [
            {"id": "deepseek-chat", "name": "DeepSeek Chat", "provider": "deepseek"},
            {"id": "deepseek-reasoner", "name": "DeepSeek Reasoner", "provider": "deepseek"},
        ],
        "nvidia": [
            {"id": "meta/llama-3.1-70b-instruct", "name": "Llama 3.1 70B Instruct", "provider": "nvidia"},
            {"id": "meta/llama-3.1-8b-instruct", "name": "Llama 3.1 8B Instruct", "provider": "nvidia"},
            {"id": "mistralai/mixtral-8x22b-instruct-v0.1", "name": "Mixtral 8x22B Instruct", "provider": "nvidia"},
        ],
    }
    return fallbacks.get(provider_id, [])
