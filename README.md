# HearU 🤝

> **"You are not alone."**

HearU is a privacy-first emotional support Android application that connects people who need to be heard (Seekers) with empathetic listeners (Givers), with an AI Companion fallback powered by **Gemini 3.7 Flash** via Firebase AI Logic.

---

## 🏗️ Architecture

```
app/
├── ai/                     # GeminiAiService (Firebase AI Logic)
├── data/
│   ├── local/
│   │   ├── dao/            # Room DAO (MessageDao)
│   │   └── entity/         # Room Entities (MessageEntity)
│   └── RolePreferences.kt  # DataStore — role + AI quota (persistent)
├── di/                     # Hilt DI Modules
├── model/                  # Domain models: User, Message, ChatSession
├── navigation/             # Type-safe NavHost with sealed Screen class
├── repository/             # ChatRepository, AuthRepository, UserRepository
├── ui/
│   ├── auth/               # LoginScreen, AuthViewModel
│   ├── chat/               # ChatScreen, AIChatScreen, ViewModels
│   ├── dialogs/            # CrisisSupportDialog, ReportUserDialog
│   ├── home/               # SeekerDashboard, GiverDashboard, ProfileScreen
│   └── theme/              # HearUTheme (Material 3, Color, Type)
└── HearUApplication.kt     # Hilt entry point
```

**Pattern:** MVVM + Repository + Single Source of Truth (Room ↔ Firestore)

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose (type-safe sealed routes) |
| DI | Hilt 2.52 |
| Local DB | Room 2.6.1 |
| Preferences | DataStore Preferences 1.1.1 |
| Auth | Firebase Authentication |
| Remote DB | Cloud Firestore |
| AI | Firebase AI Logic (Gemini 3.7 Flash) |
| Backend | Firebase Cloud Functions (Node.js) |
| Biometrics | AndroidX Biometric |
| Testing | JUnit4, Mockito, Turbine, Coroutines Test |
| Build | AGP 8.5, KSP 2.0, ProGuard/R8 |

---

## ✨ Features

### 👥 Human-to-Human Connection
- **Dual Roles:** Users can be a **Seeker** (seeking support) or a **Giver** (offering support)
- **Smart Matching:** Server-side Cloud Function matches Seekers to available online Givers using emotional tag overlap + rating scoring
- **Anonymous by Default:** Real names and emails are never exposed; profile photos are blurred until mutual consent

### 🤖 AI Companion (Gemini 3.7 Flash)
- Empathetic, warm AI that listens without judgment
- **Crisis Detection:** Pre-checks user input with keyword heuristics; Gemini also signals `[CRISIS_ALERT]` in responses
- **Persistent Daily Quota:** 50 messages/day enforced via DataStore across sessions and reboots
- Graceful error messages when AI is unavailable

### 🛡️ Safety & Crisis Protocols
- **Crisis Dialog:** Triggered automatically on distress signals — offers one-tap dialing of the **988 Suicide & Crisis Lifeline** and direct link to Lifeline online chat
- **Report & Block:** In-chat moderation with server-side alert logging
- **Firestore Security Rules:** RBAC — only session participants can read/write messages; 2,000-character limit enforced at DB level

### 🔒 Security
- Firebase Auth UID used for all IDOR-safe server-side operations
- No hardcoded credentials or demo backdoors in any build
- ProGuard/R8 minification enabled for release builds
- Biometric lock supported (on-device only, never transmitted)

### 🌐 Backend (Firebase Cloud Functions)
| Function | Trigger | Purpose |
|---|---|---|
| `matchSeekerWithGiver` | HTTPS Callable | Authenticated matching algorithm |
| `purgeExpiredChats` | Daily Cron (Pub/Sub) | 30-day auto-deletion of expired sessions |
| `onReportSubmitted` | Firestore onCreate | Moderation alert logging |

### 📴 Offline Support
- All Firestore messages are cached to Room DB on receipt
- Optimistic UI: sent messages appear instantly before cloud sync

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17
- Firebase project with Authentication, Firestore, and AI Logic enabled

### Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/authordata/hear_U_01.09.2026.git
   cd hear_U_01.09.2026
   ```

2. **Add Firebase config:**
   - Download `google-services.json` from your Firebase project console
   - Place it at `app/google-services.json`

3. **Deploy Firestore Security Rules:**
   ```bash
   firebase deploy --only firestore:rules
   ```

4. **Deploy Cloud Functions:**
   ```bash
   cd functions && npm install && firebase deploy --only functions
   ```

5. **Build & Run:**
   ```bash
   ./gradlew assembleDebug
   # or open in Android Studio and click Run
   ```

---

## 🧪 Testing

```bash
# Unit tests
./gradlew test

# Instrumented UI tests
./gradlew connectedAndroidTest
```

Test coverage includes:
- `AuthViewModelTest` — auth state machine & error propagation
- `AIChatViewModelTest` — crisis detection, quota enforcement, welcome message
- `LoginScreenTest` — Compose UI login flow

---

## 🔐 Privacy & Safety

See [PRIVACY_POLICY.md](./PRIVACY_POLICY.md) for the full policy. Key points:
- **18+ only** platform
- Chat logs purged after **30 days** automatically
- Biometric data stays **on-device only**
- No real names or emails are ever made public

---

## 📦 Deployment Checklist (Play Store)

- [x] ProGuard/R8 minification + resource shrinking enabled for release
- [x] `google-services.json` configured
- [x] `versionCode = 1`, `versionName = "1.0.0"` set
- [x] Adaptive launcher icons (all densities: mdpi → xxxhdpi)
- [x] Firestore security rules deployed
- [x] Cloud Functions deployed
- [x] No demo/hardcoded credentials in any build variant
- [x] Privacy Policy published and linked in Play Store listing
- [ ] **TODO:** Sign with production keystore (do NOT use debug keystore)
- [ ] **TODO:** `./gradlew :app:bundleRelease` to generate AAB
- [ ] **TODO:** Age rating questionnaire in Play Console (recommend 18+)
- [ ] **TODO:** Ensure `google-services.json` is in `.gitignore`

---

## 📄 License

© 2026 HearU. All rights reserved.

---

*Built with ❤️ to make emotional support more accessible.*
