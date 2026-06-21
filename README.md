<div align="center">
  <img src="docs/assets/logo.png" alt="Jarvis AI OS Logo" width="120" height="120" style="border-radius: 20px;">
  <h1 align="center">Jarvis AI OS</h1>
  <p align="center">
    <em>Your persistent AI operating system assistant</em>
  </p>

  [![CI](https://github.com/anomalyco/Jarvis-AI/actions/workflows/ci.yml/badge.svg)](https://github.com/anomalyco/Jarvis-AI/actions/workflows/ci.yml)
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
  [![Python 3.11+](https://img.shields.io/badge/python-3.11+-blue.svg)](backend/requirements.txt)
  [![Next.js](https://img.shields.io/badge/frontend-Next.js-black)](frontend/package.json)
  [![Kotlin](https://img.shields.io/badge/android-Kotlin-purple)](app/build.gradle.kts)
</div>

A persistent AI operating system assistant with memory, voice, workflows, and device automation.
Jarvis understands natural language, executes multi-step plans, remembers past conversations, and controls Android devices via accessibility services.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Frontend (Next.js)                    │
│  Web UI  ←→  WebSocket  ←→  REST API                    │
└──────────────────────┬──────────────────────────────────┘
                       │ ws://localhost:8000
┌──────────────────────▼──────────────────────────────────┐
│               Backend (FastAPI + Python)                 │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ Agent    │  │ Conversation │  │ Memory Agent     │   │
│  │Orchestr. │──│ Agent        │──│ (ChromaDB)       │   │
│  └──────────┘  └──────────────┘  └──────────────────┘   │
│  ┌──────────────────────────────────────────────────┐    │
│  │ Tool Registry (web_search, code_exec, android)   │    │
│  └──────────────────────────────────────────────────┘    │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTP / WebSocket
┌──────────────────────▼──────────────────────────────────┐
│              Android App (Kotlin / Jetpack)              │
│  ┌──────────┐  ┌────────────┐  ┌──────────────────┐     │
│  │ Chat UI  │  │ Overlay    │  │ Action Engine    │     │
│  │          │  │ Service    │  │ (Accessibility)   │     │
│  └──────────┘  └────────────┘  └──────────────────┘     │
└─────────────────────────────────────────────────────────┘
```

## Quick Start

### Prerequisites

- Python 3.11+
- Node.js 18+
- Android Studio (for the mobile app)

### Backend Setup

```bash
cd backend
cp .env.example .env
# Edit .env and add your API keys (at minimum OPENAI_API_KEY)

pip install -r requirements.txt
uvicorn app.main:app --reload
```

The API is now running at `http://localhost:8000`.

### Frontend Setup

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:3000` in your browser.

### Android App

Open `app/` in Android Studio, sync Gradle, and run on a device or emulator.

## Features

- **🧠 Multi-Agent Architecture**: Orchestrator, Conversation, Planner, Memory agents working together
- **💬 Streaming Chat**: Real-time token-by-token responses via WebSocket
- **🔧 Tool Execution**: Web search, code execution (sandboxed), Android device bridge
- **📌 Persistent Memory**: ChromaDB vector store for semantic recall across sessions
- **🎙️ Voice Toggle**: Built-in voice command support
- **🔑 Multi-Provider AI**: OpenAI, Anthropic Claude, Google Gemini, local Ollama
- **📱 Android Integration**: Overlay service, accessibility automation, app control
- **📊 Plan & Timeline**: Visual step-by-step plan execution with status tracking
- **🌙 Dark Mode**: Light/dark/system theme support

## Project Structure

```
├── backend/              # Python FastAPI backend
│   ├── app/
│   │   ├── agents/       # AI agents (orchestrator, conversation, planner, memory)
│   │   ├── database/     # ChromaDB vector store
│   │   ├── models/       # Pydantic validation models
│   │   ├── tools/        # Tool registry (web search, code exec, android)
│   │   ├── config.py     # Central configuration
│   │   └── main.py       # FastAPI app + WebSocket endpoints
│   ├── tests/            # E2E + unit tests
│   └── requirements.txt
├── frontend/             # Next.js web UI
│   ├── app/              # Page routes
│   ├── components/       # Reusable UI components
│   ├── hooks/            # WebSocket context + hook
│   └── package.json
├── app/                  # Native Android app (Kotlin)
│   ├── src/main/java/    # Activity, service, viewmodel, engine code
│   └── build.gradle.kts
├── docs/                 # Architecture & setup guides
├── .github/              # CI, issue templates, PR template
├── .env.example          # Environment variable template
└── gradle/               # Gradle wrapper + version catalog
```

## Configuration

Copy `backend/.env.example` to `backend/.env` and configure:

| Variable            | Description                    | Default                        |
|---------------------|--------------------------------|--------------------------------|
| `OPENAI_API_KEY`    | OpenAI API key (primary)       | -                              |
| `ANTHROPIC_API_KEY` | Anthropic API key (fallback)   | -                              |
| `GOOGLE_API_KEY`    | Google AI API key (fallback)   | -                              |
| `HOST`              | Server bind address            | `127.0.0.1`                    |
| `PORT`              | Server port                    | `8000`                         |
| `DEBUG`             | Enable hot reload              | `false`                        |
| `LOG_LEVEL`         | Logging verbosity              | `INFO`                         |
| `CORS_ORIGINS`      | Allowed CORS origins (comma separated) | `http://localhost:3000` |

## API Endpoints

| Method | Path               | Description                       |
|--------|--------------------|-----------------------------------|
| GET    | `/`                | Server info                       |
| GET    | `/api/health`      | Health check                      |
| GET    | `/api/memories`    | List/search stored memories       |
| POST   | `/api/proxy/chat`  | Proxy chat completion to AI API   |
| POST   | `/api/proxy/embeddings` | Proxy embedding request       |
| WS     | `/ws/chat`         | Streaming chat WebSocket          |
| WS     | `/ws/stream`       | Raw event stream WebSocket        |

## Testing

```bash
# Backend unit tests
cd backend
pytest tests/unit/ -v

# Backend e2e tests (requires running server)
pytest tests/e2e_test_streaming.py -v
```

## Security

- **SSRF Protection**: Proxy endpoints only allow requests to a predefined domain allowlist
- **Input Validation**: All WebSocket messages validated via Pydantic models
- **Size Limits**: HTTP body capped at 1MB, WebSocket messages at 64KB
- **API Key Safety**: Keys stored in client session storage (not persistent local storage)
- **XSS Prevention**: All markdown output sanitized with DOMPurify

## License

This project is licensed under the MIT License — see [LICENSE](LICENSE) for details.
