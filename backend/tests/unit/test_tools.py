"""Unit tests for ToolRegistry."""

import pytest
from app.tools import ToolRegistry


class TestToolRegistry:
    @pytest.fixture
    def registry(self):
        return ToolRegistry()

    def test_list_tools(self, registry):
        tools = registry.list_tools()
        assert isinstance(tools, dict)
        assert len(tools) > 0

    def test_list_tools_contains_expected(self, registry):
        tools = registry.list_tools()
        for expected in ["web_search", "code_exec", "android_bridge"]:
            assert expected in tools, f"Missing tool: {expected}"

    @pytest.mark.asyncio
    async def test_execute_unknown_tool(self, registry):
        with pytest.raises(KeyError):
            await registry.execute("nonexistent_tool", {})

    @pytest.mark.asyncio
    async def test_execute_android_bridge(self, registry):
        result = await registry.execute("android_bridge", {"action": "press_home"})
        assert result is not None
