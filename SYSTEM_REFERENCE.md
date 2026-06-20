# Jarvis AI System Reference

This document provides a technical overview of the Jarvis AI architecture, components, and communication protocols.

## Architecture Overview

Jarvis AI is a multi-layered autonomous system consisting of:
1.  **Mobile Client (Android)**: The primary interface and execution engine for on-device tasks.
2.  **Cognitive Backend (FastAPI)**: The "brain" that handles complex reasoning, planning, and long-term memory.
3.  **Web Dashboard (Next.js)**: A management interface for monitoring agent activity and configuring settings.

## Component Breakdown

### 1. Mobile Client (`/app`)
- **Vision Engine**: Uses Google ML Kit and MobileNet V3 for real-time OCR and UI element labeling.
- **Accessibility Service**: Intercepts UI events and executes gestures (taps, swipes) on behalf of the user.
- **Local Memory**: SQLite-based storage for short-term context and user preferences.
- **Overlay UI**: A floating "Orb" that provides real-time status updates and interaction points.

### 2. Cognitive Backend (`/backend`)
- **Planner Agent**: Decomposes high-level user goals into executable steps.
- **Memory Manager**: Handles vector embeddings for semantic search across past interactions.
- **Intent Classifier**: Identifies user goals from natural language input.

### 3. Web Dashboard (`/frontend`)
- **Real-time Monitoring**: Visualizes the agent's thought process and execution timeline.
- **Configuration**: Manage API keys, model selection, and agent personality.

## Communication Protocols

- **Mobile to Backend**: REST API (FastAPI) for planning and complex reasoning.
- **Backend to LLM**: Secure integration with OpenAI (GPT-4o) or Anthropic (Claude 3.5 Sonnet).
- **Internal Event Bus**: 21-event protocol for tracking agent state transitions.

## Data Flow

1.  **Input**: User speaks or types a command.
2.  **Observation**: Vision Engine scans the current screen.
3.  **Planning**: Backend generates a sequence of actions based on the goal and screen state.
4.  **Execution**: Accessibility Service performs the actions.
5.  **Verification**: Vision Engine confirms the UI changed as expected.

## Security & Privacy

- **Local-First**: OCR and visual labeling happen on-device.
- **Encryption**: All sensitive data is stored using Android's EncryptedSharedPreferences.
- **Approval Gates**: Critical actions require user confirmation.
