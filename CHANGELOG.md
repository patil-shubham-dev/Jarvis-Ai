# Changelog

## [4.0.0] — 2026-06-21

### Added
- Production-grade SSRF protection with domain allowlist for proxy endpoints
- Pydantic validation models for WebSocket messages (`WSMessage`)
- HTTP request body size limit middleware (1MB max)
- WebSocket message size limit (64KB max)
- Shared HTTP client with connection pooling for proxy requests
- Async ChromaDB operations via `run_in_executor` (non-blocking event loop)
- `ErrorBoundary` React component wrapping the entire UI
- Dark mode CSS variables + runtime theme toggle
- Reconnection jitter in WebSocket hook to prevent connection storms
- Room database `MIGRATION_4_5` and `MIGRATION_5_6` (destructive fallback optional)
- Markdown rendering for images, links, and tables in chat
- Critical unit tests: `test_config.py`, `test_orchestrator.py`, `test_tools.py`
- e2e tests for invalid JSON and oversized WebSocket messages
- `CHANGELOG.md`, `SECURITY.md`, `SUPPORT.md`, `CODEOWNERS`, `LICENSE`
- GitHub issue templates (bug report, feature request) and PR template
- `.env.example` with safe defaults (`HOST=127.0.0.1`, empty API key placeholders)
- `onDestroy()` lifecycle cleanup in `BaseSkill` (coroutine scope cancellation)

### Changed
- Default bind address changed from `0.0.0.0` to `127.0.0.1`
- CORS origins configurable via `CORS_ORIGINS` environment variable
- `LOG_LEVEL` environment variable replaces hardcoded `logging.INFO`
- `active_provider` now cached with `@lru_cache` (avoids repeated blocking HTTP calls)
- `AppDatabase.getInstance()` uses defined `Migration` objects instead of only `fallbackToDestructiveMigration`
- Memory agent uses async ChromaDB API to avoid event loop blocking
- Error logging uses `logger.exception()` throughout (captures stack traces)
- WebSocket input validated through Pydantic `WSMessage` model
- API keys loaded from `sessionStorage` instead of `localStorage`
- Settings page "Clear all memory" button calls `DELETE /api/memories`
- Settings gear button on main page navigates to `/settings`
- `showTimeline` resets automatically after stream ends or on error
- Screen capture handles row stride mismatch correctly
- `executeOpenApp()` refactored with `return`-on-null pattern (eliminates NPE risk)
- `JarvisAccessibilityService` instance writes/reads guarded by `synchronized` lock
- Removed unused `tasks` collection from ChromaDB init

### Fixed
- `ActionTimeline.tsx` crash on unknown step status (fallback icon/color)
- `MemoryExplorer.tsx` replaced mock data with real backend API call
- `ChatRepository.kt` passes tool definitions to LLM stream (was hardcoded `null`)
- `BootReceiver`/`ReminderReceiver`/`AppModule` now use shared `AppDatabase` singleton
- Settings page restores API keys from `sessionStorage` on load
- StreamMessage markdown rendered safely through DOMPurify
- Navigation across all pages works with proper `useRouter` integration
- `StreamMessage.tsx` now renders `[links]`, `![images]`, and `|tables|`

### Removed
- `TemporalCommandParser.kt` (unused Android class)
- `PrivacyProtector.kt` (unused Android class — ML Kit privacy)
- `RootDetection.kt` (unused Android class)
- `TaskExtensions.kt` (unused — all callers use `kotlinx.coroutines.tasks.await`)
- `backend/app/models/chat.py` (all 7 classes unused externally)
- `ExecutionResult` class from `backend/app/models/agent.py` (unused)
- `pydantic-settings` from requirements.txt (unused dependency)
- `PLAN.md` (internal planning document)
- Unused imports: `HTTPException` (main.py), `os`/`Settings` (vector_db.py), `sys` (tools/__init__.py)

## [3.0.0] — 2026-05-01

### Added
- Android overlay service for floating orb UI
- Accessibility service for screen context and automation
- WebSocket-based streaming chat
- ChromaDB vector memory store
- Multi-AI provider support (OpenAI, Anthropic, Google, Ollama)
- Skill system for app-specific behaviors (WhatsApp, Spotify, Meeting Memo)

### Changed
- Migrated from Flask to FastAPI
- Restructured frontend from CRA to Next.js App Router
- Migrated Android project to Gradle version catalog

### Fixed
- DuckDuckGo search migrated from dead API to `lite` HTML parser
- WebSocket cleanup on component unmount
- `@AndroidEntryPoint` placement in overlay service

## [2.0.0] — 2026-03-15

### Added
- Initial Android app with chat interface
- Basic accessibility service for UI interaction
- Voice command support with Porcupine wake word
- Local vision engine for screen analysis

## [1.0.0] — 2026-01-10

### Added
- Initial Python backend with Flask
- OpenAI integration for chat completions
- Basic REST API for chat
