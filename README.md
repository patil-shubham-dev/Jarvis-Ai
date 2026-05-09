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

### ⚡ Professional Workflow Orchestration
> **User:** "Jarvis, I'm feeling happy, play something matching."
> <br>**Jarvis:** Opening Spotify... Analyzing Library... Playing **'Happy'** by Pharrell Williams. 🚀

---

## 🚀 Advanced Capabilities

### 👁️ 1. Visionary Screen Perception
The **Sentinel Vision Engine** uses a dual-layer approach to understand your device:
- **Spatial Reasoning:** Locates UI elements based on visual patterns, not just static IDs.
- **Icon Intelligence:** Custom TFLite models recognize your favorite apps and actions instantly.
- **Live Context:** Understands the "State" of an app to decide the next logical step.

### 🧠 2. Continuous Learning Memory
Your Jarvis grows smarter every day through its **16-Module Core**:
- **Episodic Memory:** Recalls complex task sequences you've performed before.
- **Preference Mapping:** Automatically learns your music, social, and professional habits.
- **Semantic Search:** Instant context retrieval via a local vector database.

### 🛡️ 3. Hardened Security & Privacy
- **Zero-Cloud Memory:** Your personal data never leaves your device.
- **Biometric Vault:** Protect your agentic capabilities with Fingerprint or Face ID.
- **Encrypted Secrets:** API keys and sensitive tokens are managed by the Android Keystore.

---

## 🏗 System Architecture

```mermaid
graph TD
    User([User Intent]) --> Orb[Jarvis Reactive Orb]
    Orb --> Planner{PlannerAgent}
    
    subgraph "The Cognitive Engine"
        Planner --> Observe[VisionSkill: Real-time UI Analysis]
        Observe --> Think[LLM Decision: Strategy Mapping]
        Think --> Memory[MemoryAgent: Context Injection]
        Memory --> Think
        Think --> Act[ActionEngine: OS-Level Interaction]
    end
    
    Act --> Feedback[State Verification]
    Feedback --> Observe
    
    Planner --> Success([Mission Accomplished])
```

---

## 🔧 Pro Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Logic** | Kotlin 1.9, Coroutines, Hilt |
| **Vision** | Google ML Kit + MobileNet V3 |
| **Backend** | Python 3.11, FastAPI, WebSockets |
| **Memory** | ChromaDB + ONNX Embeddings |
| **Interface** | Framer Motion & Lottie Animations |

---

## 🛠 Quick Deployment

```bash
# Clone the repository
git clone https://github.com/patil-shubham-dev/Jarvis-Ai.git

# Initialize the Sentinel
cd Jarvis-Ai/app
./gradlew installDebug
```

> [!IMPORTANT]
> Ensure **Accessibility Services** are enabled for Jarvis to perform autonomous actions. See the [Setup Guide](docs/SETUP_GUIDE.md) for more.

---

<p align="center">
  <b>Jarvis AI: Sentinel OS</b><br>
  Developed with ❤️ by <a href="https://github.com/patil-shubham-dev">Shubham Patil</a><br>
  <i>Leading the transition to Agentic Computing.</i>
</p>
