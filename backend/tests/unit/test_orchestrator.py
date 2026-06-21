"""Unit tests for AgentOrchestrator."""

import pytest
from app.agents.orchestrator import AgentOrchestrator
from app.database.vector_db import VectorDB
from app.models.intent import IntentCategory


class TestOrchestrator:
    @pytest.fixture
    def orchestrator(self):
        db = VectorDB(persist_directory="/tmp/test_chroma")
        orch = AgentOrchestrator(db)
        yield orch

    @pytest.mark.asyncio
    async def test_fallback_response(self, orchestrator):
        result = orchestrator._fallback_response("some text")
        assert "trouble" in result
        assert "rephrase" in result

    @pytest.mark.asyncio
    async def test_process_message_empty_text(self, orchestrator):
        result = await orchestrator.process_message("test-session", "", None)
        assert result is None or "trouble" in result

    @pytest.mark.asyncio
    async def test_intent_classification_fallback(self, orchestrator, mocker):
        mocker.patch.object(orchestrator.conversation_agent, 'classify_intent',
                            side_effect=Exception("API down"))
        intent = await orchestrator._classify_intent("hello")
        assert intent.category == IntentCategory.CASUAL
        assert intent.confidence == 1.0

    @pytest.mark.asyncio
    async def test_emit_error_does_not_crash(self, orchestrator):
        async def bad_handler(event):
            raise RuntimeError("handler crash")
        orchestrator.on("thought", bad_handler)
        await orchestrator._emit("thought", {"content": "test"})
