<p align="center">
  <img src="docs/assets/logo.png" width="220" alt="Jarvis AI Logo">
</p>

<h1 align="center">🌌 JARVIS AI: SENTINEL OS</h1>
<p align="center">
  <b>The Future of Autonomous Mobile Interaction</b><br>
  <i>A professional-grade personal intelligence layer for Android.</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Release-v4.1.0--Stable-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Version">
  <img src="https://img.shields.io/badge/Architecture-Action--Think--Observe-blue?style=for-the-badge" alt="Architecture">
  <img src="https://img.shields.io/badge/Security-Biometric%20Encrypted-red?style=for-the-badge" alt="Security">
</p>

<p align="center">
  <img src="docs/assets/typing.svg" width="500" alt="Jarvis Typing Effect">
</p>

---

## 📱 Experience True Autonomy

Jarvis isn't just a chatbot; it's a **Sentinel**. It observes your screen, understands your intent, and executes tasks with human-like precision. By removing the friction between thought and action, Jarvis becomes your digital twin.

<p align="center">
  <table align="center">
    <tr>
      <td align="center">
        <img src="docs/assets/ui_1.jpg" width="250" style="border-radius: 15px; border: 2px solid #333;" alt="Jarvis UI 1"><br>
        <i>Intelligent Conversation</i>
      </td>
      <td align="center">
        <img src="docs/assets/ui_2.jpg" width="250" style="border-radius: 15px; border: 2px solid #333;" alt="Jarvis UI 2"><br>
        <i>Autonomous Control</i>
      </td>
    </tr>
  </table>
</p>

---

## 🏗 Deep Dive: How It Works

Jarvis operates on a high-fidelity **Multi-Agent Orchestration** model. Unlike simple automation scripts, Jarvis dynamically generates strategies based on real-time visual feedback.

### 🧠 The Cognitive Reasoning Loop
Every user request triggers a recursive cycle designed to handle ambiguity and environment shifts:

1.  **Intent Parsing:** The `TemporalCommandParser` decomposes natural language into time-bound objectives.
2.  **Observe (Sentinel Vision):** The `VisionSkill` captures the screen state, passing it through the `LocalVisionEngine` (OCR + Icon Recognition) to build a semantic map of the UI.
3.  **Think (Strategic Planning):** The `PlanningSkill` selects the optimal `Agent` (Spotify, WhatsApp, etc.) and calculates the necessary gestures.
4.  **Act (Action Engine):** The `ActionEngine` executes precise taps and swipes via the `AccessibilityHelper`, simulating human interaction.
5.  **Verify:** Jarvis re-scans the screen to ensure the action had the intended effect. If an app hangs or a popup appears, Jarvis adapts instantly.

---

## 🔬 Technical Logics & "Minute Details"

### 🛠 1. The ActionEngine Mechanics
The `ActionEngine` is the bridge between AI and Hardware. It doesn't just "click buttons"—it understands spatial geometry.
*   **Coordinate Normalization:** Maps LLM-generated relative coordinates (0-1000) to actual device pixel densities (DP/PX).
*   **Adaptive Gestures:** Uses bezier curves for swipes to avoid detection by anti-bot mechanisms in banking or security apps.
*   **Collision Detection:** Ensures the "Jarvis Orb" overlay doesn't block critical UI elements during a task.

### 📁 2. Memory Hierarchy (RAG v4)
Jarvis uses a tiered memory system to maintain context over weeks, not just minutes:
*   **L1 (Reactive):** The current conversation window (GPT-4o/Claude context).
*   **L2 (Episodic):** A local vector store (ChromaDB) containing the last 500 interactions, indexed by sentiment and task type.
*   **L3 (Routine):** The `RoutinePredictor` identifies patterns (e.g., "User always checks Email after Spotify") and pre-warms the necessary agents.

### 🛡 3. Safety & Sentinel Guardrails
The `SafetyEngine` acts as a real-time monitor for all autonomous actions:
*   **PII Masking:** Automatically blurs sensitive fields (passwords, credit cards) in the vision buffer before processing.
*   **Root Detection:** Refuses to execute high-privileged actions on compromised devices to protect the user.
*   **Execution Tracker:** Maintains a transparent, human-readable log of every gesture made by the AI.

---

## 🔧 Pro Tech Stack

| Layer | Technology | Key Component |
| :--- | :--- | :--- |
| **Logic** | Kotlin 1.9, Hilt | `SkillManager`, `BaseSkill` |
| **Vision** | ML Kit + TFLite | `LocalVisionEngine` |
| **Action** | Android Accessibility API | `ActionEngine`, `AccessibilityHelper` |
| **Memory** | ChromaDB + ONNX | `EpisodicMemoryDao` |
| **Strategy** | Multi-Agent LLM | `PlannerAgent`, `RoutineEngine` |

---

## 🚀 Future Roadmap: Phase 5
- [ ] **Whisper Integration:** Real-time, low-latency voice pipeline.
- [ ] **Document Intelligence:** Ingesting PDFs/Docs for contextual assistance.
- [ ] **Cross-Device Sync:** Unified Jarvis brain across Mobile and Desktop.

---

<p align="center">
  <b>Jarvis AI: Sentinel OS</b><br>
  Developed with ❤️ by <a href="https://github.com/patil-shubham-dev">Shubham Patil</a><br>
  <i>Leading the transition to Agentic Computing.</i>
</p>
