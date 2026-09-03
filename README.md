# HearU 🤝

> **"You are never alone. A safe space to be heard."**

HearU is a privacy-first emotional support Android application that connects people who need to be heard (**Seekers**) with empathetic listeners (**Givers**), accompanied by an AI Companion powered by **Gemini 3.7 Flash** via Firebase AI Logic, real-time **Audio Voice Notes with Waveforms**, **FCM Push Notifications**, and an interactive **4-7-8 Breathing & 5-4-3-2-1 Sensory Grounding Guide** for acute anxiety relief.

---

## 📸 Key Features & Capabilities

| Seeker Dashboard | Gemini 3.7 Flash AI Companion | Audio Voice Notes | 4-7-8 Breathing & Grounding | 24/7 Crisis SOS Hub |
|:---:|:---:|:---:|:---:|:---:|
| 🎯 Smart topic matching & radar scan | 🤖 Multi-turn empathetic conversation & quota | 🎤 Real-time recording & animated waveform | 🌬️ Animated pulsating orb & sensory guide | 🚨 1-tap dial 988 Lifeline & SMS text |

### 🌟 Core Experience
- **👥 Peer-to-Peer Empathetic Matching:** Real-time matching between Seekers and active online Givers based on emotional tag overlap ("Anxiety", "Loneliness", "Work Stress", "Relationship", "Grief", "Sleep").
- **🎤 Audio Voice Notes & Waveform Visualizer:** Record and play voice notes in peer chat with dynamic waveform animations, duration scrubber, and hardware/synthetic fallback.
- **🔔 FCM Push Notifications (`HearUFirebaseMessagingService`):** Notification channels for *Peer Match Requests*, *Chat Messages*, and *Emergency Crisis Alerts* with direct-to-chat deep linking.
- **✨ 3-Step Interactive Onboarding:** High-fidelity walkthrough introducing anonymity, 24/7 AI companion protocols, and 30-day ephemeral auto-delete.
- **🤖 24/7 AI Companion (Gemini 3.7 Flash):** Multi-turn conversational companion with safety filter guardrails (`HarmBlockThreshold`), input bounding, and a daily 50-message quota persisted across device reboots via `DataStore`.
- **🌬️ Calming & Grounding Tools:** Interactive 4-7-8 Breathing pacer with animated expanding circle + 5-4-3-2-1 Sensory Grounding tool for panic attacks.
- **🛡️ 24/7 Crisis Escalation & SOS:** Prominent emergency action routing to the **988 Suicide & Crisis Lifeline**, Crisis Text Line (741741), Trevor Project, and Veterans Crisis Line.
- **🔒 Privacy & Anonymity First:**
  - Strict **18+ platform policy**.
  - Profile pictures remain **cryptographically blurred** until mutual in-session consent.
  - **30-day ephemeral auto-purge:** All chat sessions and transcripts are permanently deleted after 30 days via scheduled Cloud Function.
  - **Biometric App Lock:** Optional Face ID / Fingerprint unlock via AndroidX `BiometricPrompt`.

---

## 🏗️ Architecture

```
app/
├── ai/                     # GeminiAiService (Firebase AI Logic with safety thresholds)
├── audio/                  # VoiceNoteManager (Hardware MediaRecorder + Waveform Visualizer)
├── auth/                   # BiometricHelper (AndroidX BiometricPrompt)
├── data/
│   ├── local/
│   │   ├── dao/            # Room DAO (MessageDao with indexed queries)
│   │   └── entity/         # Room Entities (MessageEntity)
│   └── RolePreferences.kt  # DataStore — role, quota, biometric & theme preferences
├── di/                     # Hilt DI Modules (AppModule, DatabaseModule)
├── model/                  # Domain models: User, Message, ChatSession
├── navigation/             # Type-safe Navigation Compose with sealed Screen class
├── repository/             # ChatRepository (SSOT), AuthRepository, UserRepository, ModerationRepository
├── service/                # HearUFirebaseMessagingService (FCM push notification handling & channels)
├── ui/
│   ├── auth/               # LoginScreen, RoleSelectionScreen, AuthViewModel
│   ├── chat/               # ChatScreen, AIChatScreen, ViewModels, VoiceNotePlayer
│   ├── dialogs/            # CrisisSupportDialog, ReportUserDialog
│   ├── emergency/          # EmergencyScreen (24/7 Crisis Directory & Hotlines)
│   ├── home/               # SeekerDashboard, GiverDashboard, ProfileScreen, MatchViewModel
│   ├── onboarding/         # OnboardingScreen (Interactive 3-Step Walkthrough)
│   ├── theme/              # HearUTheme (Material 3 DayNight, Color, Type)
│   └── tools/              # BreathingExerciseScreen (4-7-8 Breathing & Grounding Guide)
└── HearUApplication.kt     # Application class annotated with @HiltAndroidApp
```

**Pattern:** Clean Architecture + MVVM + Single Source of Truth (Room ↔ Cloud Firestore)

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI Framework | Jetpack Compose + Material 3 (BOM 2024.06.00) |
| Dependency Injection | Dagger Hilt 2.52 |
| Local Database | Room 2.6.1 (SQLite with indices & SSOT pattern) |
| Key-Value Storage | Jetpack DataStore Preferences 1.1.1 (Atomic transactions) |
| Authentication | Firebase Authentication (Email/Password & Anonymous) |
| Remote Database | Cloud Firestore (with fine-grained security rules) |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| AI Integration | Firebase AI Logic SDK (Gemini 3.7 Flash) |
| Cloud Functions | Node.js Serverless Functions (Matching, 30-Day Batch Purge, Moderation) |
| Security & Biometrics | AndroidX Biometric 1.2.0-alpha05 |
| Build System | Android Gradle Plugin 8.5.0, KSP 2.0.0, ProGuard/R8 |

---

## 🚀 Running in Android Studio

1. **Open in Android Studio:**
   - Launch Android Studio (Ladybug or newer).
   - Select **File > Open** and choose the `/Users/manirajc/hear_U_01.09.2026` folder.
   - Android Studio will automatically sync the project via the bundled Gradle 8.7 wrapper.

2. **Add Firebase Configuration (Optional for Cloud Sync):**
   - Place your `google-services.json` at `app/google-services.json`.
   - The app includes graceful local fallback simulation so all UI, Matchmaking, Voice Notes, and AI features run smoothly even in offline demo mode.

3. **Run the App:**
   - Select the `app` configuration and press **Run (Shift + F10)** on your emulator or physical device.

---

## 🧪 Testing Suite

```bash
# Run unit tests
./gradlew test

# Run UI / Compose instrumented tests
./gradlew connectedAndroidTest
```
