<<<<<<< HEAD
# JARVIS — AI Personal Assistant for Android

A production-grade Android AI assistant that combines a Claude-powered conversational brain with full phone automation. Speak naturally in Hindi, English, or Hinglish to send WhatsApp messages, make calls, set alarms, control system settings, and more — all while building a persistent memory of your preferences and habits.

---

## Features

- **Conversational AI** — Powered by Anthropic Claude with streaming word-by-word responses
- **Wake Word** — Say "Jarvis" to activate from any app (background service)
- **Phone Automation** — WhatsApp, SMS, calls, alarms, app launching, WiFi, Bluetooth, volume, brightness, navigation
- **Multi-step Commands** — "Text mummy I'll be late, then set an alarm for 8 PM"
- **Persistent Memory** — Learns your habits, contacts, and preferences across sessions via Room DB
- **Secure Vault** — AES-256 + Android Keystore encrypted storage for passwords and API keys, locked behind Biometric/PIN
- **TTS Speaker Button** — Toggle voice responses per-session like ChatGPT
- **Auto-TTS** — Always speaks when triggered by voice or phone action
- **Hinglish Support** — Natural Hindi + English mixed voice recognition
- **Memory Viewer** — Browse and delete what Jarvis knows about you
- **Clean Onboarding** — First-run permissions setup with clear ADB instructions

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
| Architecture | MVVM + Repository pattern |
| Encryption | AES-256-GCM via Android Keystore |
| Biometrics | AndroidX Biometric 1.1.0 |
| HTTP Client | OkHttp 4.12.0 |
| UI | Material Components 1.12.0 |

---

## Project Structure

```
android/
├── app/src/main/
│   ├── java/com/jarvis/assistant/
│   │   ├── JarvisApp.kt                  # Application class
│   │   ├── core/
│   │   │   ├── api/ClaudeApi.kt          # Streaming Claude API client
│   │   │   ├── commands/CommandExecutor.kt # Phone action dispatcher
│   │   │   ├── crypto/VaultCrypto.kt     # AES-256 encryption
│   │   │   └── prefs/Prefs.kt            # SharedPreferences wrapper
│   │   ├── data/
│   │   │   ├── db/                       # Room DAOs + Database
│   │   │   ├── models/Entities.kt        # Room entity data classes
│   │   │   └── repository/MemoryExtractor.kt
│   │   ├── services/
│   │   │   ├── JarvisAccessibilityService.kt  # Phone control
│   │   │   ├── JarvisListenerService.kt       # Wake word (foreground)
│   │   │   └── BootReceiver.kt
│   │   └── ui/
│   │       ├── chat/          # ChatActivity + ChatViewModel + ChatAdapter
│   │       ├── memory/        # MemoryActivity
│   │       ├── settings/      # SettingsActivity
│   │       ├── vault/         # VaultActivity (biometric locked)
│   │       └── onboarding/    # SplashActivity + PermissionsActivity
│   └── res/
│       ├── layout/            # All XML layouts
│       ├── drawable/          # Shape drawables
│       ├── values/            # Colors, themes, strings
│       ├── xml/               # Accessibility service config
│       └── mipmap-*/          # Launcher icons (all densities)
├── build.gradle
├── settings.gradle
└── gradle.properties
=======
<div align="center">

# ⚡ Jarvis AI

### Android AI Assistant with Memory, Context & Device Control

<img src="https://img.shields.io/badge/Android-API%2026%2B-4FC3F7?style=for-the-badge&logo=android"/>
<img src="https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin"/>
<img src="https://img.shields.io/badge/Architecture-MVVM-00C853?style=for-the-badge"/>
<img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge"/>

</div>

---

## ✨ Overview

Jarvis AI is a customizable Android assistant designed to go beyond chat.

It combines:

* conversational AI
* system-level control
* memory-driven intelligence

to create a personal assistant that adapts over time.

---

## 🔥 Core Features

* 💬 Chat-based AI interface
* 🎙️ Voice input (Speech-to-Text)
* 🔊 Text-to-Speech responses
* 🪟 Floating overlay assistant
* ♿ Accessibility-based device control
* 🔑 Bring your own API key
* 🌙 Dark UI for continuous usage

---

## ⚡ Device Control Capabilities

Jarvis is designed to interact with your device using system services.

* Open apps
* Navigate UI using accessibility
* Perform repetitive actions
* Assist with daily workflows
* Overlay assistant across apps

> Control is limited by Android permissions and user approval.

---

## 🧠 Memory System

Jarvis is built around a structured memory architecture.

### Core Capabilities

* Store user data
* Retrieve relevant context
* Adapt responses based on history
* Improve behavior over time

---

## 🧠 Jarvis Core Memory Architecture

```
/JARVIS
│
├── CORE_IDENTITY/
├── SOCIAL_GRAPH/
├── BEHAVIORAL_INTELLIGENCE/
├── KNOWLEDGE_BASE/
├── MEMORY_TIMELINE/
├── PREFERENCES_ENGINE/
├── LIFE_OPERATIONS/
├── COMMUNICATIONS/
├── DIGITAL_FOOTPRINT/
├── DECISION_ENGINE/
├── HEALTH_PROFILE/
├── FINANCIAL_SYSTEM/
├── SECURITY_VAULT/
├── LEARNING_ENGINE/
├── CONTEXT_ENGINE/
└── SYSTEM_LOGS/
>>>>>>> bfb04b9 (initial commit: Jarvis AI cleaned version)
```

---

<<<<<<< HEAD
## Setup Instructions

### Prerequisites

- Android Studio Ladybug / Meerkat 2024+ (or Otter 2025.2.2)
- JDK 21
- Android device with USB Debugging enabled
- Anthropic API key — get one at [console.anthropic.com](https://console.anthropic.com)

### 1. Clone the repository

```bash
git clone https://github.com/yourusername/jarvis-android.git
cd jarvis-android
```

### 2. Open in Android Studio

`File → Open → select the android/ folder`

Wait for Gradle sync to complete.

### 3. Run on your device

Connect your Android phone via USB, enable USB Debugging, then click **Run ▶**.

### 4. First launch

The onboarding screen will prompt you to grant:
- Microphone
- Contacts
- Phone/Calls
- Notifications
- Accessibility Service (required for WhatsApp automation)

### 5. Add your API key

`Top bar → Settings (gear icon) → paste your Claude API key → Save`

The model will be auto-populated. You can change it in Settings.

### 6. Grant ADB permissions (one-time, for brightness + system control)

With the phone connected via USB and USB Debugging enabled:

```bash
adb shell pm grant com.jarvis.assistant android.permission.WRITE_SECURE_SETTINGS
=======
### 🧍 Core Identity

Defines who the user is.

* profile
* goals
* personality
* strengths

---

### 👥 Social Graph

Tracks relationships and interactions.

* family, friends, connections
* interaction history
* importance weighting

Used for:

* smarter replies
* reminders
* social context

---

### 🧠 Behavioral Intelligence

Learns real user patterns.

* habits
* routines
* distractions
* productivity cycles

---

### 📚 Knowledge Base

What the user knows.

* academic data
* skills
* interests
* projects

---

### 🕒 Memory Timeline

Tracks life events.

* daily logs
* achievements
* lessons

---

### 🎯 Preferences Engine

Learns choices.

* UI preferences
* content preferences
* decision styles

---

### ⚙️ Life Operations

Execution layer.

* tasks
* reminders
* scheduling

---

### 💬 Communications

Stores interactions.

* chats
* AI conversations
* communication patterns

---

### 🌐 Digital Footprint

Tracks usage patterns.

* app usage
* behavior signals

---

### 🧠 Decision Engine

Improves decision making.

* past decisions
* risk profile
* pattern learning

---

### ❤️ Health Profile

Optional personal tracking.

* physical
* mental
* lifestyle

---

### 💰 Financial System

Tracks money flow.

* expenses
* subscriptions
* investments

---

### 🔐 Security Vault

Sensitive data storage.

* encrypted credentials
* API keys
* private data

> Must use strong encryption (e.g. AES-based storage)

---

### 🤖 Learning Engine

Self-improving intelligence.

* user pattern models
* predictions
* embeddings

---

### 🔄 Context Engine

Real-time awareness.

* current activity
* active tasks
* environment

---

### 📊 System Logs

Transparency layer.

* actions
* errors
* decisions

---

## ⚡ Advanced Intelligence Layer

### 1. Vector Memory Layer

* Convert data into embeddings
* Store in vector database
* Enable semantic search & recall

---

### 2. Agent System

* Planner Agent → decides what to do
* Memory Agent → retrieves context
* Action Agent → executes tasks
* Communication Agent → interacts with user

---

### 3. Continuous Learning Loop

Observe → Analyze → Update → Predict → Act

Jarvis improves itself by learning from user behavior and context over time.

---

## 🚀 Getting Started

### 1. Clone

```bash
git clone https://github.com/your-username/jarvis-ai.git
cd jarvis-ai
```

---

### 2. Open in Android Studio

Let Gradle sync.

---

### 3. Add API Key

Open app → Settings → Paste API key

> No API keys are included

---

### 4. Run

```bash
./gradlew installDebug
>>>>>>> bfb04b9 (initial commit: Jarvis AI cleaned version)
```

---

<<<<<<< HEAD
## Build Instructions

### Debug APK

```bash
cd android
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Release APK

```bash
./gradlew assembleRelease
=======
## 🏗️ Project Structure

```
app/
 ├── ui/
 ├── viewmodel/
 ├── data/
 ├── api/
 ├── utils/
>>>>>>> bfb04b9 (initial commit: Jarvis AI cleaned version)
```

---

<<<<<<< HEAD
## Voice Commands

| Command | Action |
|---|---|
| "Call mum" | Makes a phone call |
| "Text Rahul on WhatsApp I'm on my way" | Sends WhatsApp message (confirms first) |
| "Set alarm for 6 AM tomorrow" | Creates an alarm |
| "Open Spotify" | Launches the app |
| "Turn off WiFi" | Opens WiFi panel |
| "Set volume to 60" | Adjusts media volume |
| "Navigate to CP Delhi" | Opens Google Maps navigation |
| "Play Arijit Singh on Spotify" | Launches Spotify search |
| "Text mummy I'm busy and set a reminder in 30 mins" | Multi-step command |

---

## Architecture Notes

**Memory system** — Every Claude response is parsed for `<<<MEMORY>>>` blocks. Extracted facts (user name, habits, relationships, preferences) are stored in Room DB and injected into every subsequent prompt as context. The last 30 memories are always in the system prompt.

**Streaming** — The Claude API is called with `"stream": true`. The `Flow<String>` from `ClaudeApi.streamChat()` emits each SSE chunk, which `ChatViewModel` appends to the live message in the RecyclerView.

**Command detection** — Claude is instructed to respond with pure JSON for phone actions. `CommandExecutor.tryExecute()` checks if the response starts with `{` and dispatches accordingly. Sensitive actions (calls, messages) return `requiresConfirmation = true` and surface a confirmation card.

---

## Contribution Guidelines

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit with clear messages: `git commit -m "feat: add feature description"`
4. Push and open a pull request

Please follow the existing code style — no unnecessary comments, clean abstractions, meaningful names.

---

## License

```
MIT License

Copyright (c) 2025

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
```
=======
## 🧠 Tech Stack

* Kotlin
* Android SDK
* MVVM Architecture
* Retrofit
* Coroutines

---

## 🤝 Contributing

Contributions are welcome.

* Keep code simple and readable
* Follow existing structure
* Open PR with clear description

---

## ⚠️ Notes

* This project is experimental
* Some features depend on system permissions
* Not affiliated with any AI provider

---

## 📄 License

MIT License

---

## ⭐ Support

Star the repo if you find it useful.
>>>>>>> bfb04b9 (initial commit: Jarvis AI cleaned version)
