# Buddy AI

**Autonomous, Natural-Language Android Phone Control & Execution Assistant**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#system-requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Target Platform](https://img.shields.io/badge/Platform-arm64--v8a%20%7C%20armeabi--v7a%20%7C%20x86__64-orange)](#architecture)
[![Pass Rate](https://img.shields.io/badge/Tests-236%20Passing%20(100%25)-success)](#test-suite--verification)

Buddy AI is a fully functional, English-only Android AI assistant engineered to perform compound, real-world tasks on your phone through hands-free voice commands. By uniting on-device offline acoustic models with high-speed language model providers and Android Accessibility services, Buddy AI navigates user interfaces, interacts with third-party applications, manages local files, and answers multi-turn contextual queries.

---

## Key Capabilities

* **Hands-Free Offline Voice Engine:** Completely offline keyword spotting ("Hey Buddy") and offline streaming speech recognition powered by [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) Zipformer models. Zero raw voice data leaves the device during ambient listening.
* **On-Device Neural TTS:** Powered by [Kokoro](https://huggingface.co/hexgrad/Kokoro-82M), delivering natural, multi-voice British and American English neural speech with sentence-pipelined streaming playback.
* **Autonomous Phone Control Loop:** Employs an iterative *Observe → Plan → Validate → Execute → Observe → Verify* state machine to interact with installed applications via Android AccessibilityNodeInfo hierarchies, gesture dispatching, and screen OCR/vision.
* **Multi-Provider Intelligence:** Native integration with high-speed inference providers including **Groq** (Qwen, Llama), **Google Gemini**, **OpenRouter**, alongside on-device or local network instances via **LM Studio**, **Ollama**, and custom OpenAI-compatible endpoints.
* **Compound Multi-Step Tools:** Equipped with autonomous tool calling for live web searches, device geographic location lookups, and scoped file system operations (creation, appending, directory creation).
* **Deterministic Safety & Privacy:** Code-level safeguards prevent unauthorized execution of irreversible actions (calling, messaging, payment submission) without explicit confirmation.
* **Dynamic Island Overlay:** A hardware-accelerated, animated status overlay displaying real-time audio waveforms, current operational states, and quick-action controls.

---

## Architectural Overview

```
                            +-----------------------------------+
                            |       User Voice: "Hey Buddy"     |
                            +-----------------+-----------------+
                                              |
                                              v
                            +-----------------------------------+
                            |   sherpa-onnx KWS Keyword Spotter |
                            +-----------------+-----------------+
                                              |
                                              v
                            +-----------------------------------+
                            |  sherpa-onnx Streaming English ASR|
                            +-----------------+-----------------+
                                              |
                                              v
                            +-----------------------------------+
                            |     FastCommandRouter / Intent     |
                            +--------+-----------------+--------+
                                     |                 |
                   (Local Fast Path) |                 | (Compound / Agent Path)
                                     v                 v
            +-----------------------------+   +-----------------------------+
            |      FastActionExecutor     |   |          PhoneAgent         |
            | - Media, Alarms, Flashlight |   | - Multi-step LLM Planning   |
            | - Volume, Basic App Launch  |   | - get_location, search_web  |
            +--------------+--------------+   | - file_operation, a11y tap  |
                           |                  +--------------+--------------+
                           |                                 |
                           +----------------+----------------+
                                            |
                                            v
                            +-----------------------------------+
                            |         Kokoro Neural TTS         |
                            +-----------------+-----------------+
                                              |
                                              v
                            +-----------------------------------+
                            |   Dynamic Island Status Overlay   |
                            +-----------------------------------+
```

---

## Subsystem Details

### 1. Mandatory Offline Voice Pipeline
1. **Keyword Spotting (KWS):** Uses an optimized ONNX acoustic model to detect the wake phrase **"Hey Buddy"** in real time from the device microphone with adaptive background noise estimation (`MicGain`).
2. **Streaming ASR:** Employs a streaming Zipformer transducer to transcribe continuous English speech without cloud latency or internet connectivity.
3. **Neural TTS:** Generates expressive speech via Kokoro 82M across 11 distinct English voices (default: `en_GB-george-medium`). Text is segmented by `SpeechChunks` and generated concurrently with playback to eliminate inter-sentence delays.

### 2. Autonomous UI Automation (`PhoneAgent`)
* Inspects active window layouts using Android's `AccessibilityService` API.
* Performs targeted element clicks, text input via `ACTION_SET_TEXT`, directional scrolling, and touch coordinate gestures (`dispatchGesture`).
* Intercepts locked-app scenarios and biometric authentication prompts, pausing execution safely and prompting the user to unlock.
* Built-in application layout tips optimize interactions for major applications including WhatsApp, Google Maps, Chrome, and Settings.

### 3. Safety Enforcement & Sensitive Actions
Outbound communication and destructive actions enforce strict checks:
* Code-level validation via `SensitiveActions.needsConfirmation` prevents unintended execution of calls, message transmissions, and payments.
* Explicit user requests (e.g. `"Send message to Amma as hi through WhatsApp"`) are verified and executed with end-to-end delivery confirmation.

---

## System Requirements

| Parameter | Specification |
| :--- | :--- |
| **Operating System** | Android 8.0 (API level 26) through Android 16 (API level 36) |
| **Supported ABIs** | `arm64-v8a` (recommended), `armeabi-v7a`, `x86_64` |
| **Runtime Storage** | ~300 MB free storage (application + offline acoustic models) |
| **Memory** | 3 GB RAM minimum (4 GB+ recommended for optimal neural synthesis) |
| **Verified Hardware** | Vivo I2302 / iQOO Neo 9 Pro (Snapdragon 8 Gen 2 / MediaTek Dimensity) |

---

## Setup & First-Time Configuration

1. **Install Application:** Deploy the generated debug or release APK matching your target ABI:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
   ```
2. **System Permissions:** Launch **Buddy AI** and navigate to the permissions dashboard to enable:
   * **Assistant Role:** Set Buddy AI as the default digital assistant.
   * **Accessibility Service:** Enable `Buddy AI phone control` for UI automation.
   * **Display Overlay:** Allow display over other apps for the Dynamic Island.
   * **Microphone:** Grant background audio recording permissions.
   * **Battery Optimization:** Exclude from aggressive battery optimization to ensure consistent foreground service execution.
3. **Configure AI Provider:**
   * Open the **Models** tab.
   * Choose an AI provider (e.g., **Groq**, **Google Gemini**, or **OpenRouter**).
   * Tap the **Get API Key ↗** button to open the provider's developer console directly in your browser.
   * Paste your API key, select your preferred model from the live model dialog, and star the profile as primary.

---

## Building from Source

### Prerequisites
* Java Development Kit (JDK): OpenJDK 17 (`17.0.12+7` or newer)
* Android SDK Platform Tools (API 34/35/36)
* Gradle 8.11.1+ (included via Gradle Wrapper)

### Compilation
Clone the repository and run the Gradle build:

```bash
# Clone the repository
git clone https://github.com/suhasram180407/buddyai.git
cd buddyai

# Download native runtime dependencies (sherpa-onnx)
./gradlew fetchSherpaOnnx

# Execute automated unit test suite (236 tests)
./gradlew testDebugUnitTest

# Assemble architecture-specific debug APKs
./gradlew assembleDebug
```

Outputs will be generated under:
`app/build/outputs/apk/debug/`

---

## Automated Test Suite

Buddy AI includes comprehensive unit and integration test coverage across all subsystems:
* **Audio & Recognition:** Real audio pipeline tests (`RealEngineTest`), transcription cleanup (`CleanTranscriptTest`).
* **Router & Intent Evaluation:** Parameterized fast-command validation (`FastCommandRouterTest`), policy routing (`RoutePolicyTest`).
* **Memory & Storage:** SQLite transactional consistency and versioning (`MemoryStoreTest`, `MemoryManagerTest`).
* **Agent Safeguards:** Sensitive action confirmation boundaries (`SensitiveActionsTest`).
* **Application Flows:** End-to-end integration workflows (`AppFlowTest`, `TaskReplayTest`).

**Test Status:** 236 passed, 0 failed, 100% success rate.

---
Licensed under the [MIT License](LICENSE).
