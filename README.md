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
```

---

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
```

---

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
```

---

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
