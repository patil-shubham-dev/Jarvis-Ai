"""Unit tests for config module."""

import os
from app.config import AppConfig


def test_default_config_values():
    cfg = AppConfig()
    assert cfg.app_name == "Jarvis AI OS Backend"
    assert cfg.debug is False
    assert "localhost:3000" in cfg.cors_origins
    assert cfg.ws_ping_interval == 30


def test_get_api_key_from_env(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test-key")
    cfg = AppConfig()
    key = cfg.get_api_key()
    assert key == "sk-test-key"


def test_get_provider_api_key(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "sk-openai")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "sk-ant-test")
    cfg = AppConfig()
    assert cfg.get_provider_api_key("openai") == "sk-openai"
    assert cfg.get_provider_api_key("anthropic") == "sk-ant-test"
    assert cfg.get_provider_api_key("nonexistent") == ""


def test_is_allowed_proxy_url():
    cfg = AppConfig()
    assert cfg.is_allowed_proxy_url("https://api.openai.com/v1/chat/completions") is True
    assert cfg.is_allowed_proxy_url("http://localhost:11434/api/tags") is True
    assert cfg.is_allowed_proxy_url("https://evil.com/steal") is False
    assert cfg.is_allowed_proxy_url("http://169.254.169.254/latest/meta-data/") is False
    assert cfg.is_allowed_proxy_url("") is False


def test_active_provider_falls_back_to_openai(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    monkeypatch.delenv("GOOGLE_API_KEY", raising=False)
    cfg = AppConfig()
    provider = cfg.active_provider
    assert provider == "openai"
