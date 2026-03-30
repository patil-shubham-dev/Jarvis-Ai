<div align="center">

<h1>⚡ Jarvis AI</h1>

<p><strong>A production-grade Android AI assistant with voice control, device automation, persistent memory, and a secure vault.</strong><br/>
Built for people who want more than a chatbot on their phone.</p>

<p>
  <img src="https://img.shields.io/badge/Android-API%2026%2B-4FC3F7?style=for-the-badge&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white"/>
  <img src="https://img.shields.io/badge/Claude%20API-Anthropic-D4A017?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Architecture-MVVM-00C853?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Status-WIP-orange?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge"/>
</p>

<!-- REPLACE THIS with a real demo GIF: app/screenshots/demo.gif -->
> 📸 **[Demo GIF — drop in a screen recording here]**

</div>

---

## Overview

Jarvis is a deeply personal AI assistant for Android. It goes beyond simple Q&A — it controls your phone, remembers your habits, speaks in Hindi/English/Hinglish, and stores sensitive data in an encrypted vault behind biometrics.

The core idea: your assistant should get *smarter the longer you use it*, not reset every session.

> ⚠️ **Status:** Core features (chat, voice, memory, vault, accessibility automation) are functional. Some phone control capabilities and edge-case commands are still being refined.

---

## Screenshots

<!-- REPLACE THESE with real screenshots. Suggested layout: Chat → Voice → Memory → Vault -->

| Chat Interface | Voice Mode | Memory Viewer | Secure Vault |
|:-:|:-:|:-:|:-:|
| ![Chat](https://via.placeholder.com/200x400?text=Chat+UI) | ![Voice](https://via.placeholder.com/200x400?text=Voice+Mode) | ![Memory](https://via.placeholder.com/200x400?text=Memory+Viewer) | ![Vault](https://via.placeholder.com/200x400?text=Vault+UI) |

---

## Features

### 🧠 Conversational AI
- Powered by **Anthropic Claude** with SSE streaming (word-by-word responses)
- Full **conversation history** maintained per session
- **Multi-step commands** — *"Text Rahul I'm running late, then set an alarm for 9 PM"*

### 🎙️ Voice
- Wake word **"Jarvis"** — activates from any app via background foreground service
- **Hinglish support** — natural Hindi + English mixed voice recognition
- **TTS responses** — toggle voice per-session; always-on when triggered by voice or phone action

### 📱 Phone Automation
Via Android Accessibility Service:

| Command | Action |
|---|---|
| `"Call mum"` | Makes a phone call |
| `"Text Rahul on WhatsApp I'm on my way"` | Sends WhatsApp message (confirms first) |
| `"Set alarm for 6 AM tomorrow"` | Creates an alarm |
| `"Open Spotify"` | Launches the app |
| `"Turn off WiFi"` | Opens WiFi panel |
| `"Set volume to 60"` | Adjusts media volume |
| `"Navigate to CP Delhi"` | Opens Google Maps navigation |
| `"Text mum I'm busy and remind me in 30 mins"` | Executes multi-step command |

> Sensitive actions (calls, messages) always surface a confirmation card before executing.

### 🗃️ Persistent Memory
- Extracts facts from every conversation (`<<<MEMORY>>>` blocks in Claude responses)
- Stores name, habits, relationships, preferences in **Room DB**
- Last 30 memories injected into every system prompt automatically
- **Memory Viewer** — browse, inspect, and delete what Jarvis knows about you

### 🔐 Secure Vault
- **AES-256-GCM** encryption via Android Keystore
- Locked behind **Biometric / PIN** authentication
- Stores passwords, API keys, private notes

---

## Memory Architecture

Jarvis uses a structured memory model — not a flat chat log.

```
JARVIS_MEMORY/
├── CORE_IDENTITY/         ← who you are: name, goals, personality
├── SOCIAL_GRAPH/          ← contacts, relationships, interaction history
├── BEHAVIORAL_INTELLIGENCE/  ← habits, routines, distractions
├── KNOWLEDGE_BASE/        ← skills, interests, projects
├── MEMORY_TIMELINE/       ← daily logs, achievements, lessons
├── PREFERENCES_ENGINE/    ← UI, content, decision styles
├── LIFE_OPERATIONS/       ← tasks, reminders, scheduling
├── COMMUNICATIONS/        ← chat history, message patterns
├── DECISION_ENGINE/       ← past decisions, risk profile
├── SECURITY_VAULT/        ← encrypted credentials, API keys
└── CONTEXT_ENGINE/        ← current activity, real-time awareness
```

This architecture enables **semantic search**, **context injection**, and eventually — **vector-based memory retrieval**.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| Build System | Gradle 8.11.1 + AGP 8.8.2 |
| Java Version | 21 |
| AI Backend | Anthropic Claude API (SSE streaming) |
| Local Database | Room 2.6.1 |
| Architecture | MVVM + Repository |
| Encryption | AES-256-GCM via Android Keystore |
| Biometrics | AndroidX Biometric 1.1.0 |
| HTTP Client | OkHttp 4.12.0 |
| UI | Material Components 1.12.0 |

---

## Project Structure

```
android/
└── app/src/main/java/com/jarvis/assistant/
    ├── core/
    │   ├── api/ClaudeApi.kt              # Streaming Claude API client (SSE)
    │   ├── commands/CommandExecutor.kt   # Phone action dispatcher
    │   ├── crypto/VaultCrypto.kt         # AES-256 encryption
    │   └── prefs/Prefs.kt               # SharedPreferences wrapper
    ├── data/
    │   ├── db/                           # Room DAOs + Database
    │   ├── models/Entities.kt            # Room entity data classes
    │   └── repository/MemoryExtractor.kt
    ├── services/
    │   ├── JarvisAccessibilityService.kt # Phone control via a11y
    │   ├── JarvisListenerService.kt      # Wake word (foreground service)
    │   └── BootReceiver.kt
    └── ui/
        ├── chat/         # ChatActivity + ChatViewModel + ChatAdapter
        ├── memory/        # MemoryActivity
        ├── settings/      # SettingsActivity
        ├── vault/         # VaultActivity (biometric locked)
        └── onboarding/   # SplashActivity + PermissionsActivity
```

---

## Getting Started

### Prerequisites

- Android Studio Ladybug / Meerkat 2024+
- JDK 21
- Android device — USB Debugging enabled
- Anthropic API key → [console.anthropic.com](https://console.anthropic.com)

### 1. Clone

```bash
git clone https://github.com/patil-shubham-dev/jarvis-ai.git
cd jarvis-ai
```

### 2. Open in Android Studio

`File → Open → select the android/ folder`

Wait for Gradle sync.

### 3. Run on device

Connect via USB, enable USB Debugging, click **Run ▶**.

### 4. First launch

Grant the following on the onboarding screen:
- Microphone
- Contacts
- Phone / Calls
- Notifications
- **Accessibility Service** ← required for WhatsApp and app automation

### 5. Add your API key

`Settings (gear icon) → Paste your Claude API key → Save`

The model name will be auto-populated. You can change it in Settings.

### 6. One-time ADB permission (brightness + system control)

```bash
adb shell pm grant com.jarvis.assistant android.permission.WRITE_SECURE_SETTINGS
```

### Build a debug APK

```bash
cd android
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

---

## Architecture Notes

**Streaming** — Claude API is called with `"stream": true`. `ClaudeApi.streamChat()` returns a `Flow<String>` emitting each SSE chunk. `ChatViewModel` appends each chunk to the live message in real time.

**Command detection** — Claude is prompted to return pure JSON for phone actions. `CommandExecutor.tryExecute()` checks if the response starts with `{` and dispatches accordingly.

**Memory extraction** — Every Claude response is parsed for `<<<MEMORY>>>` blocks. Extracted facts are stored in Room DB and injected into every subsequent system prompt (last 30 entries).

---

## Roadmap

- [x] Streaming chat with Claude
- [x] Wake word activation
- [x] Accessibility-based phone control
- [x] Persistent Room DB memory
- [x] AES-256 encrypted vault
- [x] Hinglish voice recognition
- [ ] Vector embeddings for semantic memory search
- [ ] On-device ML for intent classification
- [ ] Widget / lock screen overlay
- [ ] Multi-device memory sync

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit clearly: `git commit -m "feat: describe what you did"`
4. Push and open a pull request

Follow the existing code style — no unnecessary comments, clean abstractions, meaningful names.

---

## License

```
MIT License — Copyright (c) 2025

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
```

---

<div align="center">
  <sub>Built with curiosity. If this saves you time, drop a ⭐</sub>
</div>
