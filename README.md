# JARVIS AI — Personal Intelligence Operating System

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Version: 4.5.0 Premium](https://img.shields.io/badge/Version-4.5.0_Premium-blue.svg)](#)

---

## 🌌 Overview

**Jarvis AI** is a sophisticated, local-first personal intelligence layer for Android. It transforms your device from a tool into a proactive partner by combining **Long-term Episodic Memory**, **Autonomous Planning**, and **Professional Device Automation**. 

Unlike traditional assistants, Jarvis operates with a "Privacy-First" philosophy—all your memories, habits, and personal data stay on your device. It bridges the gap between a chatbot and a true AI Operating System.

---

## 🚀 Key Features

### 1. Hybrid Learning & Episodic Memory (New)
Jarvis doesn't just chat; it learns. The **Layer C: Continuous Learning Loop** implements a hybrid system:
- **Lightweight Extraction:** Automatically distills facts, preferences, and successful workflows from every conversation.
- **Deep Behavioral Analysis:** Periodically analyzes logs to identify recurring patterns (e.g., "Messaging boss at 9 AM when late").
- **Proactive Routines:** Jarvis identifies habits and proactively proposes automation routines for user confirmation.

### 2. Futuristic "Orb" UI/UX (New)
The interface has been redesigned to feel like a premium, intelligent entity:
- **The Jarvis Orb:** A central, living element in the main screen with breathing animations and dynamic states (Idle, Thinking, Acting).
- **Glassmorphism Design:** Subtle frosted-glass chat bubbles, thin borders, and soft shadows for a minimal, premium aesthetic.
- **Ambient Glow:** Dark matte backgrounds with radial glows that react to the AI's state.

### 3. 16-Module Core Memory
A comprehensive persistent memory architecture covering every aspect of your life:
- **Social Graph:** Relationships and communication history.
- **Behavioral Intelligence:** Learned habits and routines.
- **Financial & Health:** Securely tracked personal stats.
- **Knowledge Base:** Learned facts and research insights.

### 4. Autonomous Multi-Agent Orchestration
- **PlannerAgent:** Decomposes complex intents into executable task graphs.
- **MemoryAgent:** Handles semantic recall and context injection.
- **ActionEngine:** Executes human-like interactions (clicks, scrolls, typing) via Accessibility Services.

### 5. Universal AI Provider (BYOK)
Bring your own API key and switch between providers seamlessly:
- **OpenAI** (GPT-4o, GPT-4o-mini)
- **Anthropic** (Claude 3.5 Sonnet/Opus)
- **Groq** (Ultra-fast Llama/Mixtral)
- **Mistral & Nvidia** support included.

---

## 🛠 Detailed Setup Manual

### 1. Prerequisites
- **Android Device:** Android 8.0 (Oreo) or higher.
- **Storage:** ~100MB free space.
- **API Key:** At least one key from OpenAI, Anthropic, or Groq.

### 2. Installation
- **Option A (APK):** Download the latest release from the [Releases](https://github.com/patil-shubham-dev/Jarvis-Ai/releases) page.
- **Option B (Source):**
  ```bash
  git clone https://github.com/patil-shubham-dev/Jarvis-Ai.git
  cd Jarvis-Ai
  ./gradlew assembleDebug
  ```

### 3. Essential Permissions
For Jarvis to function as a "True Agent," you must enable:
1. **Accessibility Service:** Required for Jarvis to "see" your screen and "act" on your behalf.
   - *Settings > Accessibility > Installed Services > Jarvis AI > ON.*
2. **Display Over Other Apps:** Required for the floating Jarvis Orb and status overlays.
   - *Settings > Apps > Special Access > Display over other apps > Jarvis AI > ON.*
3. **Microphone:** Required for voice commands and wake-word detection.

### 4. Configuration (BYOK)
1. Open Jarvis and tap the **Settings** (Gear) icon.
2. Paste your API Key. Jarvis will **auto-detect** the provider.
3. Select your preferred model (e.g., `gpt-4o`).
4. (Optional) Add a **Picovoice AccessKey** in the Voice section for always-on "Hey Jarvis" detection.

---

## 🧠 How it Works: The Architecture

Jarvis operates on a multi-layered brain architecture:

1.  **Perception Layer:** Uses `VisionSkill` and `ScreenStateEngine` to understand what's on your screen.
2.  **Cognition Layer:** The `PlannerAgent` uses the LLM to decide the next step based on your goal and current context.
3.  **Memory Layer:** `VectorMemoryStore` provides semantic search across your 16 modules, injecting relevant history into the prompt.
4.  **Action Layer:** `ActionEngine` translates AI intents into actual Android gestures and system calls.

---

## 🔒 Privacy & Security

- **Local-First:** Your memory modules are stored as encrypted JSON files on your device.
- **Zero Tracking:** No telemetry or personal data is sent to our servers.
- **Biometric Lock:** Enable in settings to protect your conversations with fingerprint/face ID.

---

## 🤝 Contributing

We welcome contributions! Whether it's adding new `Skills`, improving the `LearningEngine`, or refining the `UI`.
1. Fork the repo.
2. Create your feature branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for more information.

---

**Jarvis AI — Not just a chatbot, but your digital twin.** 🚀
