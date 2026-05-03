# JARVIS AI — Complete Setup Guide

This guide walks you through every step to get Jarvis AI running professionally on your Android device.

---

## Step 1: Installation

### Option A: Download Pre-Built APK

1. Visit the [Releases](https://github.com/patil-shubham-dev/Jarvis-Ai/releases) page.
2. Download the latest `jarvis-ai-release.apk`.
3. On your Android device, open the APK file and tap "Install".
4. Grant any requested permissions.

### Option B: Build from Source

**Requirements:** Android Studio, Gradle, JDK 17+

```bash
git clone https://github.com/patil-shubham-dev/Jarvis-Ai.git
cd Jarvis-Ai
./gradlew assembleRelease
```

The APK will be in `app/build/outputs/apk/release/`.

---

## Step 2: Initial Permissions

When you first launch Jarvis, you'll be prompted for several permissions:

### Accessibility Service (Required for Device Automation)

1. Tap "Enable Accessibility Service" in the onboarding screen.
2. You'll be taken to Settings > Accessibility.
3. Scroll down and find "Jarvis AI" under Installed Services.
4. Tap it and toggle "Use Jarvis AI" to ON.
5. Return to the app.

### Overlay Permission (For Floating Bubble)

1. In the app, go to Settings > Overlay.
2. Tap the toggle.
3. You'll be taken to Settings > Apps > Special App Access > Display over other apps.
4. Find "Jarvis AI" and toggle it ON.
5. Return to the app.

### Microphone Permission (For Voice Input)

1. Grant when prompted, or manually enable in Settings > Apps > Jarvis AI > Permissions > Microphone.

---

## Step 3: Configure Your AI Provider

This is the most important step. Jarvis needs an API key to access AI models.

### Getting an API Key

Choose one of these providers:

**OpenAI (Recommended for beginners)**
- Visit [platform.openai.com/api-keys](https://platform.openai.com/api-keys)
- Click "Create new secret key"
- Copy the key (starts with `sk-`)
- Add $5-20 to your account for usage

**Anthropic (Claude)**
- Visit [console.anthropic.com](https://console.anthropic.com)
- Navigate to API Keys
- Create a new key (starts with `sk-ant-`)
- Add credits to your account

**Groq (Fast & Free Tier)**
- Visit [console.groq.com](https://console.groq.com)
- Create an account
- Generate an API key (starts with `gsk_`)
- Free tier includes generous usage limits

**Mistral (European Alternative)**
- Visit [console.mistral.ai](https://console.mistral.ai)
- Create an API key
- Add credits

### Adding Your Key to Jarvis

1. Open the Jarvis app.
2. Tap the **Settings** icon (gear) in the top-right.
3. In the "API Key" field, paste your key.
4. Jarvis will automatically detect your provider and available models.
5. Select your preferred model from the dropdown (e.g., "gpt-4o" for OpenAI).
6. Tap **Save Configuration**.

---

## Step 4: Enable Voice Intelligence (Optional)

For always-on "Hey Jarvis" wake word detection:

1. In Settings, toggle **Voice Intelligence** to ON.
2. You'll be prompted for a Picovoice AccessKey.
3. Visit [console.picovoice.ai](https://console.picovoice.ai) and create a free account.
4. Create a new AccessKey (free tier available).
5. Copy and paste it into the dialog.
6. Tap **Enable**.

Jarvis will now listen for "Hey Jarvis" in the background without draining battery.

---

## Step 5: Customize Settings

### Text-to-Speech (TTS)

Toggle **Text-to-Speech** to have Jarvis speak responses aloud. Select your preferred voice in system settings if desired.

### Biometric Lock

Toggle **Biometric Lock** to require fingerprint or face unlock when opening the app. This protects your memory and conversations.

### Overlay Bubble

Toggle **Overlay** to show a floating Jarvis bubble on your home screen for quick access.

---

## Step 6: First Interaction

1. Return to the main chat screen.
2. Type a message like "Hello Jarvis" or "What can you do?"
3. Tap the **Send** button (arrow icon).
4. Watch as Jarvis streams a response in real-time.

---

## Step 7: Explore Memory Dashboard

1. Swipe left on the chat screen to open the **Sidebar**.
2. Tap **Memory Dashboard** to see Jarvis's 16 intelligence modules.
3. Each module stores different aspects of your data (identity, social graph, preferences, etc.).
4. All data is stored locally on your device.

---

## Advanced Configuration

### Custom Models

If your provider supports multiple models, you can switch between them:

1. In Settings, select a different model from the dropdown.
2. Tap **Save Configuration**.
3. Your next message will use the new model.

### Accessibility Service Customization

For advanced users who want to customize device automation:

1. Edit `ActionEngine.kt` to add new action types.
2. Rebuild the app with `./gradlew assembleDebug`.

### Memory Module Inspection

To view your memory modules:

1. Connect your device to a computer via USB.
2. Enable USB Debugging in Developer Options.
3. Use Android Studio's Device File Explorer to browse `/data/data/com.jarvisai.app/files/jarvis_memory/`.
4. Each JSON file represents a memory module.

---

## Troubleshooting

### "API Key not recognized"

- Ensure the key is valid and at least 20 characters.
- Check that you've selected the correct provider.
- Verify that your account has credits/balance.

### Accessibility Service keeps disabling

- Some devices auto-disable services after a few hours. Re-enable in Settings > Accessibility.
- Ensure Jarvis is not in the battery optimization list: Settings > Battery > Battery Optimization > Jarvis > Don't optimize.

### Voice Intelligence not working

- Verify Picovoice AccessKey is correct.
- Check that microphone permissions are granted.
- Ensure the device is not in silent mode.

### Messages not saving

- Verify storage permissions are granted.
- Check that your device has at least 100 MB free storage.

### Slow responses

- Check your internet connection.
- Ensure your API provider is not rate-limited.
- Try a faster model (e.g., `gpt-4o-mini` instead of `gpt-4o`).

---

## Performance Tips

1. **Use Faster Models:** Opt for `-mini` or `-fast` variants for quicker responses.
2. **Limit Memory Modules:** Disable unused modules to reduce memory overhead.
3. **Clear Old Logs:** Periodically clear system logs to free up storage.
4. **Disable Overlay When Not Needed:** The floating bubble consumes battery.

---

## Privacy & Security Checklist

- [ ] API key is stored encrypted on your device.
- [ ] All memory modules are local; no data leaves your device without permission.
- [ ] Biometric lock is enabled to protect unauthorized access.
- [ ] Accessibility Service is only used for device automation, not data collection.
- [ ] Regular backups of memory modules are recommended (manual export).

---

## Next Steps

1. **Explore Device Automation:** Ask Jarvis to "Open WhatsApp" or "Search for restaurants near me".
2. **Build Your Memory:** Interact regularly so Jarvis learns your preferences and patterns.
3. **Customize Your Prompt:** Edit the system prompt in `CommunicationAgent.kt` to personalize Jarvis's behavior.
4. **Contribute:** If you find bugs or have feature ideas, open an issue on GitHub.

---

## Support

For issues or questions:

- Check the [GitHub Issues](https://github.com/patil-shubham-dev/Jarvis-Ai/issues) page.
- Review this guide again for common problems.
- Open a new issue with detailed information about your problem.

---

**Happy chatting with Jarvis! 🚀**
