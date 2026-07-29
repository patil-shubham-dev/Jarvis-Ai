"""Unit tests for providers module (detection, config, model fetching)."""

import pytest
from app.providers import (
    detect_provider,
    get_provider_config,
    _parse_provider_models,
    _fallback_models,
    PROVIDER_CONFIGS,
    PROVIDER_PATTERNS,
)


class TestDetectProvider:
    """API key pattern matching and provider detection."""

    def test_detect_openai(self):
        assert detect_provider("sk-proj-abc123") == "openai"
        assert detect_provider("sk-abc123def456") == "openai"

    def test_detect_anthropic(self):
        assert detect_provider("sk-ant-abc123") == "anthropic"

    def test_detect_google(self):
        assert detect_provider("AIzaSyABC123def456") == "google"

    def test_detect_groq(self):
        assert detect_provider("gsk_abc123") == "groq"

    def test_detect_deepseek(self):
        assert detect_provider("sk-deepseek-abc123") == "deepseek"

    def test_detect_openrouter(self):
        assert detect_provider("sk-or-v1-abc123") == "openrouter"
        assert detect_provider("sk-or-abc123") == "openrouter"

    def test_detect_mistral(self):
        assert detect_provider("a" * 32) == "mistral"
        assert detect_provider("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6") == "mistral"

    def test_detect_nvidia(self):
        assert detect_provider("nvapi-abc123") == "nvidia"

    def test_detect_empty_key(self):
        assert detect_provider("") is None

    def test_detect_none_key(self):
        assert detect_provider(None) is None

    def test_detect_unknown_key(self):
        assert detect_provider("unknown-prefix-abc") is None

    def test_detect_short_mistral_excluded(self):
        """Mistral pattern requires 32+ chars. Shorter strings should not match."""
        assert detect_provider("short") is None

    def test_detect_sk_fallback(self):
        """Keys starting with sk- that aren't caught by other patterns fall back to openai."""
        assert detect_provider("sk-something-unknown") == "openai"

    def test_all_providers_have_patterns(self):
        """Every provider in PROVIDER_CONFIGS should have a pattern in PROVIDER_PATTERNS."""
        for pid in PROVIDER_CONFIGS:
            assert pid in PROVIDER_PATTERNS, f"Missing pattern for provider: {pid}"


class TestGetProviderConfig:
    """Provider configuration lookups."""

    def test_get_openai_config(self):
        cfg = get_provider_config("openai")
        assert cfg is not None
        assert cfg["name"] == "OpenAI"
        assert cfg["base_url"] == "https://api.openai.com/v1"
        assert cfg["default_model"] == "gpt-4o-mini"
        assert cfg["api_key_header"] == "Authorization"

    def test_get_anthropic_config(self):
        cfg = get_provider_config("anthropic")
        assert cfg is not None
        assert cfg["name"] == "Anthropic"
        assert "x-api-key" in cfg["api_key_header"]

    def test_get_google_config(self):
        cfg = get_provider_config("google")
        assert cfg is not None
        assert "Gemini" in cfg["name"]
        assert cfg["api_key_prefix"] == ""

    def test_get_unknown_provider(self):
        assert get_provider_config("nonexistent") is None

    def test_all_configs_have_required_keys(self):
        required = {"name", "base_url", "models_endpoint", "chat_endpoint",
                     "default_model", "api_key_header", "api_key_prefix"}
        for pid, cfg in PROVIDER_CONFIGS.items():
            missing = required - set(cfg.keys())
            assert not missing, f"Provider '{pid}' missing keys: {missing}"

    def test_all_configs_have_unique_names(self):
        names = [cfg["name"] for cfg in PROVIDER_CONFIGS.values()]
        assert len(names) == len(set(names)), "Duplicate provider names found"


class TestParseProviderModels:
    """Model list parsing from provider API responses."""

    def test_parse_openai_models(self):
        data = {
            "data": [
                {"id": "gpt-4o"},
                {"id": "gpt-4o-mini"},
                {"id": "text-embedding-3-small"},  # should be excluded
                {"id": "whisper-1"},               # should be excluded
                {"id": "dall-e-3"},                 # should be excluded
                {"id": "gpt-3.5-turbo"},
            ]
        }
        models = _parse_provider_models("openai", data)
        ids = [m["id"] for m in models]
        assert "gpt-4o" in ids
        assert "gpt-4o-mini" in ids
        assert "gpt-3.5-turbo" in ids
        assert "text-embedding-3-small" not in ids
        assert "whisper-1" not in ids
        assert "dall-e-3" not in ids

    def test_parse_anthropic_models(self):
        data = {
            "data": [
                {"id": "claude-sonnet-4-20250514", "display_name": "Claude Sonnet 4"},
                {"id": "claude-haiku-3-20240307", "display_name": "Claude Haiku 3"},
            ]
        }
        models = _parse_provider_models("anthropic", data)
        assert len(models) == 2
        assert models[0]["name"] == "Claude Sonnet 4"

    def test_parse_google_models(self):
        data = {
            "models": [
                {
                    "name": "models/gemini-2.0-flash",
                    "displayName": "Gemini 2.0 Flash",
                    "supportedGenerationMethods": ["generateContent", "embedContent"],
                },
                {
                    "name": "models/text-embedding-004",
                    "displayName": "Text Embedding",
                    "supportedGenerationMethods": ["embedContent"],
                },
            ]
        }
        models = _parse_provider_models("google", data)
        ids = [m["id"] for m in models]
        assert "gemini-2.0-flash" in ids
        assert "text-embedding-004" not in ids

    def test_parse_empty_data(self):
        assert _parse_provider_models("openai", {}) == []
        assert _parse_provider_models("anthropic", {"data": []}) == []
        assert _parse_provider_models("google", {"models": []}) == []

    def test_parse_unknown_provider(self):
        assert _parse_provider_models("unknown", {"data": [{"id": "test"}]}) == []

    def test_parse_groq_uses_openai_parser(self):
        data = {"data": [{"id": "llama-3.3-70b-versatile"}, {"id": "mixtral-8x7b-32768"}]}
        models = _parse_provider_models("groq", data)
        assert len(models) == 2


class TestFallbackModels:
    """Static fallback model lists when API is unreachable."""

    def test_fallback_openai_has_gpt4o(self):
        models = _fallback_models("openai")
        ids = [m["id"] for m in models]
        assert "gpt-4o" in ids
        assert "gpt-4o-mini" in ids

    def test_fallback_anthropic(self):
        models = _fallback_models("anthropic")
        assert len(models) >= 1

    def test_fallback_google(self):
        models = _fallback_models("google")
        assert len(models) >= 2

    def test_fallback_all_known_providers(self):
        for pid in PROVIDER_CONFIGS:
            models = _fallback_models(pid)
            assert len(models) > 0, f"No fallback models for {pid}"

    def test_fallback_unknown_provider(self):
        assert _fallback_models("nonexistent") == []

    def test_fallback_models_have_correct_structure(self):
        for pid in PROVIDER_CONFIGS:
            for model in _fallback_models(pid):
                assert "id" in model
                assert "name" in model
                assert "provider" in model
                assert model["provider"] == pid


class TestProviderConfigsComplete:
    """Ensure provider configurations are internally consistent."""

    def test_all_patterns_match_configs(self):
        """Every pattern in PROVIDER_PATTERNS should match a config."""
        for pid in PROVIDER_PATTERNS:
            assert pid in PROVIDER_CONFIGS, f"No config for pattern: {pid}"

    def test_embedding_endpoints(self):
        """Only providers with embedding endpoints should be used for embeddings."""
        has_embedding = {"openai", "google", "mistral"}
        for pid, cfg in PROVIDER_CONFIGS.items():
            if pid in has_embedding:
                assert cfg.get("embedding_endpoint") is not None, f"{pid} should have embedding endpoint"
            else:
                assert cfg.get("embedding_endpoint") is None, f"{pid} should NOT have embedding endpoint"

    def test_provider_names_have_no_trailing_spaces(self):
        for pid, cfg in PROVIDER_CONFIGS.items():
            assert cfg["name"] == cfg["name"].strip(), f"{pid} name has trailing spaces"

    def test_default_models_format(self):
        for pid, cfg in PROVIDER_CONFIGS.items():
            model = cfg["default_model"]
            assert isinstance(model, str) and len(model) > 0, f"{pid} has invalid default model"
