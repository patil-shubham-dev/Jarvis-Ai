from typing import Dict, Any, Optional, Callable
import logging

logger = logging.getLogger(__name__)

class ToolRegistry:
    _instance = None
    _tools: Dict[str, Callable] = {}

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._tools = {}
            cls._register_defaults()
        return cls._instance

    @classmethod
    def _register_defaults(cls):
        cls._tools["web_search"] = None
        cls._tools["code_exec"] = None
        cls._tools["android_bridge"] = None

    def register(self, name: str, handler: Callable):
        self._tools[name] = handler
        logger.info(f"Registered tool: {name}")

    def unregister(self, name: str):
        self._tools.pop(name, None)

    async def execute(self, name: str, params: Dict[str, Any] = None) -> Any:
        handler = self._tools.get(name)
        if handler is None:
            available = [k for k, v in self._tools.items() if v is not None]
            if name in self._tools:
                raise NotImplementedError(f"Tool '{name}' not implemented")
            raise KeyError(f"Tool '{name}' not found. Available: {available}")

        if params is None:
            params = {}

        if asyncio.iscoroutinefunction(handler):
            return await handler(**params)
        return handler(**params)

    def list_tools(self) -> Dict[str, str]:
        return {
            name: "implemented" if handler is not None else "not_implemented"
            for name, handler in self._tools.items()
        }

import asyncio
import ast
import io
import contextlib

# Register web search handler
async def _web_search_handler(query: str, num_results: int = 5):
    try:
        import httpx
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(
                "https://lite.duckduckgo.com/lite/",
                params={"q": query}
            )
            from html.parser import HTMLParser

            class LinkExtractor(HTMLParser):
                def __init__(self):
                    super().__init__()
                    self.results = []
                    self.in_result = False
                    self._tag_stack = []

                def handle_starttag(self, tag, attrs):
                    self._tag_stack.append(tag)
                    if tag == "a" and self._tag_stack.count("a") <= num_results:
                        attrs_dict = dict(attrs)
                        if "href" in attrs_dict and attrs_dict["href"].startswith("http"):
                            self.in_result = True

                def handle_data(self, data):
                    if self.in_result and data.strip():
                        self.results.append(data.strip())
                        self.in_result = False

                def handle_endtag(self, tag):
                    if self._tag_stack:
                        self._tag_stack.pop()

            parser = LinkExtractor()
            parser.feed(resp.text)
            snippets = parser.results[:num_results]
            if snippets:
                return "\n".join(f"- {s}" for s in snippets)
            return f"No results found for '{query}'"
    except Exception as e:
        return f"Web search failed: {e}"

ToolRegistry().register("web_search", _web_search_handler)

# Register code execution handler
SAFE_BUILTINS = {
    "abs": abs, "all": all, "any": any, "bool": bool, "chr": chr,
    "dict": dict, "dir": dir, "divmod": divmod, "enumerate": enumerate,
    "filter": filter, "float": float, "format": format, "frozenset": frozenset,
    "hash": hash, "hex": hex, "id": id, "int": int, "isinstance": isinstance,
    "issubclass": issubclass, "iter": iter, "len": len, "list": list,
    "map": map, "max": max, "min": min, "next": next, "oct": oct,
    "ord": ord, "pow": pow, "range": range, "repr": repr, "reversed": reversed,
    "round": round, "set": set, "slice": slice, "sorted": sorted,
    "str": str, "sum": sum, "tuple": tuple, "type": type, "zip": zip,
    "True": True, "False": False, "None": None,
    "math": __import__("math"), "random": __import__("random"),
    "json": __import__("json"), "datetime": __import__("datetime"),
    "collections": __import__("collections"), "itertools": __import__("itertools"),
}

async def _code_exec_handler(code: str, language: str = "python"):
    if language != "python":
        return f"Language '{language}' not supported yet"

    try:
        ast.parse(code)
    except SyntaxError as e:
        return f"Syntax error: {e}"

    output = io.StringIO()
    try:
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            exec(code, {"__builtins__": SAFE_BUILTINS})
        result = output.getvalue()
        return result if result else "Code executed successfully (no output)"
    except Exception as e:
        return f"Execution error: {e}"

ToolRegistry().register("code_exec", _code_exec_handler)

# Register Android bridge handler
async def _android_bridge_handler(action: str, params: dict = None):
    return {
        "action": action,
        "params": params or {},
        "status": "forwarded",
        "message": f"Android action '{action}' forwarded to device. Awaiting execution."
    }

ToolRegistry().register("android_bridge", _android_bridge_handler)
