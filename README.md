# JARVIS AI — Personal Intelligence Operating System

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Version: 4.5.0 Premium](https://img.shields.io/badge/Version-4.5.0_Premium-blue.svg)](#)

---

## Overview

Jarvis is a sophisticated, local-first personal AI assistant for Android. It combines advanced on-device memory management, multi-agent intelligence orchestration, and professional device automation into a single, privacy-respecting ecosystem. Unlike cloud-dependent assistants, Jarvis keeps your data local, your intelligence distributed, and your autonomy intact.

### Key Differentiators

**Local-First Architecture:** All memory, intelligence, and decision-making happens on your device. No data leaves unless you explicitly authorize it.

**16-Module Memory System:** Jarvis maintains a sophisticated persistent memory architecture covering identity, social connections, behavioral patterns, financial data, health profiles, and more—all stored securely on your device.

**Multi-Agent Intelligence:** The system employs specialized agents for planning, memory management, and communication, enabling autonomous decision-making and proactive assistance.

**Universal AI Provider Support:** Bring your own API key from OpenAI, Anthropic, Groq, Mistral, or Nvidia. Jarvis auto-detects your provider and adapts seamlessly.

**Advanced Device Automation:** Jarvis can read your screen, click UI elements, open apps, send messages, and search the web—all through a professional accessibility service.

**Claude-Level UI/UX:** A warm, professional interface with glassmorphism design, real-time token streaming, and a memory dashboard for visualizing your intelligence modules.

---

## Installation & Setup

### Prerequisites

- Android 8.0+ (API 26+)
- 100 MB free storage
- At least one AI provider API key (OpenAI, Anthropic, etc.)

### Quick Start

1. **Download the APK** from the Releases page or build from source.
2. **Grant Permissions:** When prompted, enable Accessibility Services and Overlay permissions.
3. **Configure Your AI Provider:** In Settings, paste your API key. Jarvis will auto-detect your provider.
4. **Enable Voice Intelligence (Optional):** Add your Picovoice AccessKey for always-on wake word detection.
5. **Start Chatting:** Open the main chat interface and begin interacting with Jarvis.

### Building from Source

```bash
git clone https://github.com/patil-shubham-dev/Jarvis-Ai.git
cd Jarvis-Ai
./gradlew assembleDebug
```

The APK will be generated in `app/build/outputs/apk/debug/`.

---

## Core Features

### 1. Local-First Intelligence

Jarvis operates entirely on your device. All memory modules (identity, social graph, behavioral intelligence, etc.) are stored as encrypted JSON files and vector embeddings. This ensures absolute privacy and zero external data leaks.

### 2. 16-Module Memory System

The intelligence backbone consists of sixteen specialized modules:

| Module | Purpose |
|--------|---------|
| CORE_IDENTITY | User profile, name, preferences |
| SOCIAL_GRAPH | Contacts, relationships, communication patterns |
| BEHAVIORAL_INTELLIGENCE | Habits, routines, decision patterns |
| KNOWLEDGE_BASE | Learned facts, research, and insights |
| MEMORY_TIMELINE | Chronological events and milestones |
| PREFERENCES_ENGINE | App preferences, UI settings, personalization |
| LIFE_OPERATIONS | Calendar, tasks, deadlines |
| COMMUNICATIONS | Chat history, message templates |
| DIGITAL_FOOTPRINT | Browsing history, app usage |
| DECISION_ENGINE | Past decisions and their outcomes |
| HEALTH_PROFILE | Fitness, sleep, wellness data |
| FINANCIAL_SYSTEM | Budget, transactions, financial goals |
| SECURITY_VAULT | Encrypted credentials and sensitive data |
| LEARNING_ENGINE | Continuous learning and model updates |
| CONTEXT_ENGINE | Current device state and foreground app |
| SYSTEM_LOGS | Audit trail and system events |

### 3. Multi-Agent Orchestration

Three specialized agents work in harmony:

**PlannerAgent:** Analyzes user intent and selects the appropriate action (AI response, device control, memory update, or web search).

**MemoryAgent:** Handles semantic recall, memory persistence, and context aggregation from the 16 modules.

**CommunicationAgent:** Refines AI responses into the professional Jarvis persona and manages system prompts.

### 4. Advanced Device Automation

Through the Accessibility Service, Jarvis can:

- Read screen content and understand the current app
- Click UI elements and navigate the interface
- Open applications by package name
- Send WhatsApp messages and other communications
- Manage calendar events
- Control WiFi and Bluetooth settings
- Adjust device volume and Do Not Disturb mode
- Set screen brightness
- Perform web searches
- Execute any action available through Android intents

### 5. Voice Intelligence

With Picovoice Porcupine integration, Jarvis listens for the "Hey Jarvis" wake word offline, without draining battery. Once triggered, it streams audio to your configured AI provider for processing.

---

## Recent Improvements

This update focuses on enhancing Jarvis's core capabilities, making it more versatile and responsive:

- **Expanded Device Control:** Implemented direct control for WiFi, Bluetooth, Volume, and Do Not Disturb (DND) mode. Brightness control now intelligently opens display settings and attempts to adjust the slider via accessibility.
- **Multi-App Skill Library:** Introduced new agents for Email (Gmail) and Calendar (Google Calendar) automation, allowing Jarvis to send emails and manage events directly. These are integrated through new `CommunicationSkill` and `PlanningSkill` modules.
- **Dynamic Skill Discovery:** The `SkillManager` now dynamically advertises its capabilities to the `PlannerAgent`, enabling more robust and adaptable task planning. New skills are automatically learned by the planner without requiring manual code changes.

---

## Configuration

### API Keys

1. Open **Settings** in the app.
2. Paste your API key in the **API Key** field.
3. Jarvis will auto-detect your provider and available models.
4. Select your preferred model and tap **Save**.

### Supported Providers

- **OpenAI:** `sk-...` keys
- **Anthropic:** `sk-ant-...` keys
- **Groq:** `gsk_...` keys
- **Mistral:** `sk-...` keys
- **Nvidia:** `nvapi-...` keys

---

## Privacy & Security

**Encryption:** All sensitive data (API keys, credentials, memory) is encrypted using Android's EncryptedSharedPreferences.

**Local Storage:** Memory modules are stored locally and never transmitted without explicit user action.

**Biometric Protection:** Optional fingerprint/face unlock ensures only authorized users access Jarvis.

---

## Troubleshooting

### "Could not detect provider"
Ensure your API key is valid and at least 20 characters long. Check that you've entered the correct key format for your provider.

### Accessibility Service not working
Verify that Jarvis is enabled in Settings > Accessibility > Installed Services.

### Voice Intelligence not triggering
Ensure Picovoice AccessKey is correctly entered and the service is running.

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) file for details.

---
