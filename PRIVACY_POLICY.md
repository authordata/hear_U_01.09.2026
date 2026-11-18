# Privacy Policy for HearU
**Effective Date:** September 1, 2026

## 1. Introduction
HearU ("we", "our", or "us") provides a platform designed to foster supportive peer emotional connections. Your privacy and safety are our highest priorities.

## 2. Strict 18+ Age Policy
HearU is strictly restricted to individuals aged 18 and older. We do not knowingly collect personal information from minors.

## 3. Data Collection & Use
- **Account Information:** Email address (if registering via Email/Password) or an anonymous Firebase UID (if using Anonymous Guest mode), used solely for authentication and abuse prevention.
- **Profile & Safety Preferences:** Display name (alias), emotion tags, role preference (`seeker`, `giver`, or `both`), Firebase Cloud Messaging (FCM) push notification token, and your user block list (`blockedUsers`). Real names and email addresses are never shown to peers.
- **Profile Avatars:** Profile avatars remain visually blurred by default in peer sessions and are only revealed upon mutual, revocable, explicit in-session consent from both participants.
- **Voice Notes & Microphone Access:** When you explicitly tap to record a voice note, HearU requests `RECORD_AUDIO` permission to capture audio into a temporary `.m4a` file in app-private cache storage along with duration and waveform amplitude metadata. HearU never listens in the background. Cached voice files are removed on sign-out, account deletion, or the 30-day retention purge.
- **Biometrics:** Biometric authentication uses AndroidX `BiometricPrompt` backed by the Android OS keystore. Biometric templates never leave your device and are never accessed or transmitted by HearU.
- **Local Storage & Backup:** Offline message cache (Room SQLite) and app preferences (DataStore) reside in app-private storage with Android cloud backup and device-to-device data extraction disabled.

## 4. 30-Day Ephemeral Chat Retention
To minimize data exposure, peer chat sessions and messages stored in Cloud Firestore are automatically purged 30 days after creation via scheduled server deletion jobs (`purgeExpiredChats`). AI Companion conversations run ephemerally in session memory on-device and are not persisted to chat servers. Local cached messages are cleared when you sign out, delete your account, or exceed the 30-day retention window.

## 5. AI Interactions (Gemini 2.0 Flash)
Conversations with the AI Companion are processed via the Firebase Vertex AI SDK using Gemini 2.0 Flash. Inputs are processed solely to generate supportive responses within the active session, are subject to daily quota limits, and are governed by safety filters.

## 6. Safety, Moderation & Crisis Escalation
HearU employs automated keyword heuristics and user reporting tools. If indicators of immediate self-harm or emergency distress are detected, HearU surfaces direct 24/7 crisis resources (such as 988 Suicide & Crisis Lifeline and 911/112). When a user submits an abuse report, report metadata (`reporterId`, `reportedUserId`, `sessionId`, `reason`, `description`, `timestamp`, `status`) is stored for moderation review.

## 7. Account & Data Deletion
You can permanently delete your account and erase associated profile and local data directly inside the app via **Profile -> Delete Account & Erase Data**. Deleting your account removes your Firebase Auth record, `/users/{uid}` profile document, DataStore preferences, Room local message cache, and temporary audio cache. Minimal moderation/safety report records may be retained where necessary for abuse prevention and legal compliance.

## 8. Contact & Support
For privacy questions, data deletion assistance, or moderation inquiries, contact: `privacy@hearu.app`.
