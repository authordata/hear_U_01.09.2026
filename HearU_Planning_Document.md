# HearU — App Planning Document
> **Emotional Connection Platform for Android**
> Version: 1.1 | Date: September 1, 2026

---

## 1. Vision & Overview

**HearU** is a free Android platform that connects people who need emotional support (*Seekers*) with people willing to offer it (*Givers*). It offers human-to-human matching, AI-powered companionship (Gemini 3.7 Flash), and a safe, verified environment.

### Core Principles
- 🔒 **Safe & Verified** — Strict 18+ age gate, verified via email + phone.
- 🤝 **Human First** — Real matched conversations, AI only as fallback for Seekers.
- 🛡️ **Protected** — Crisis detection, moderation, and automated auto-deletion for privacy.
- 🌍 **Multilingual** — English, Hindi, French, Spanish, Telugu.
- 💚 **Free** — No paywalls. Emotional support should be accessible to all.

---

## 2. User Roles

### 2.1 Emotional Seeker
Someone seeking emotional support, a listening ear, or guidance.
- Browses Giver profiles or receives algorithm matches.
- Initiates chat sessions with Givers.
- Access to AI Chat (Gemini 3.7 Flash) capped at **50 messages/day**.
- Can rate Givers after each session.

### 2.2 Emotional Giver
Someone willing to listen and provide peer-level emotional support.
- Any user (18+) can sign up.
- Can earn a "Trained Volunteer" badge via an **in-app automated text/quiz module**.
- Manages availability: **Online / Offline / Busy (DND)**.
- Waits to be matched or selected (cannot initiate).

### 2.3 Dual Role Users
- A single account can hold **both** roles.
- After login: **Role Switcher** inside the app ("Today I want to: Seek / Give").

---

## 3. Onboarding & Sign-Up Flows

> **CRITICAL LEGAL RULE**: The platform is strictly **18+ only** to eliminate COPPA liabilities and safeguard against minor-adult interactions.

### 3.1 Sign-Up (Both Roles)
```
1. Enter full name + date of birth (Strict age gate: 18+)
2. Enter email address → receive email verification link
3. Enter phone number → receive SMS OTP
4. Choose initial role (Seeker or Giver)
5. Select emotion tags / support topics
6. Set display name (real name hidden)
7. Upload Profile Photo (Stored blurred; unblurred only after in-chat mutual consent)
8. Profile complete → Dashboard
```

---

## 4. Authentication & Security

### 4.1 Login Flow
To balance maximum security with user retention (UX):
- **First Login / New Device:** Email + Password AND Phone OTP required.
- **Daily Access:** Uses **Android BiometricPrompt** (Fingerprint/FaceID) or Device PIN. Session is kept active securely.

### 4.2 Tech Stack: Firebase Authentication
- Email/password auth + Phone OTP via `firebase-auth`.
- Biometric authentication via `androidx.biometric:biometric`.
- **Firebase BoM**: `34.18.0`

---

## 5. Matching System Architecture

### 5.1 Algorithm-Based Matching (Cloud-Side)
To protect user data and prevent Seekers from downloading the entire Giver database, matching is executed securely via a **Firebase Cloud Function**.

| Factor | Weight |
|--------|--------|
| Emotion tags overlap | 35% |
| Topic overlap | 30% |
| Giver availability (Online preferred) | 20% |
| Giver rating score | 10% |
| Language preference match | 5% |

### 5.2 Offline Fallback
If no matched Givers are currently online, the app displays:
> *"No matches available right now. Want to chat with our AI companion?"* (Button routes to Gemini).

---

## 6. Chat System & Data Retention

### 6.1 Human-to-Human Chat
- **Text only**.
- Profile photos remain heavily blurred until both users tap "Reveal Identity".
- **Data Retention:** All chats are **auto-deleted after 30 days**. A Cloud Function runs a daily cron job to purge expired chats, saving Firestore costs and maximizing privacy.

### 6.2 AI Chat (Gemini 3.7 Flash)
- **Rate Limit:** Hard cap of **50 messages per day** per Seeker to control API costs.
- Uses **Firebase AI Logic SDK** (`com.google.firebase:firebase-ai`).

**System Prompt for Gemini:**
```
You are a warm, empathetic AI companion on HearU — an emotional support platform.
Your role is to listen, validate feelings, and offer gentle guidance. You are NOT
a therapist. Always remind users that professional help is available when needed.
If you detect any signs of self-harm, crisis, or abuse — immediately surface
emergency resources and alert the platform.
Language: Respond in the same language the user writes in.
```

---

## 7. Safety, Moderation & Admin

### 7.1 Crisis Detection Protocol
Triggered when AI or moderation detects self-harm, suicidal ideation, or abuse.
```
1. Pause the chat UI immediately.
2. Display full-screen crisis card (iCall, Vandrevala, 112/911).
3. Notify platform admin via Cloud Function alert.
```

### 7.2 Web Admin Dashboard
A dedicated **React/Next.js Web Admin Panel** will be built alongside the Android app.
- Allows the moderation team to review reported chats, crisis alerts, and ban users.
- Connects directly to the same Firebase project.

---

## 8. App Architecture & Tech Stack

### 8.1 Project Architecture
**MVVM + Clean Architecture** (Jetpack Compose + Hilt).

### 8.2 Tech Stack (Latest Versions — September 2026)
| Component | Technology | Version |
|-----------|-----------|---------|
| UI | Jetpack Compose + Material3 | BOM 2024.06+ |
| Auth / DB / Cloud | Firebase (Auth, Firestore, Functions) | BoM 34.18.0 |
| AI | Firebase AI Logic (Gemini 3.7 Flash) | BoM 34.18.0 |
| Biometrics | AndroidX Biometric | 1.2.0-alpha05+ |
| Target / Compile SDK | Android 16 | API 36 |
| Admin Panel | Next.js + React | Web |

---

## 9. Development Phases (Updated)

### Phase 1 — Foundation (Weeks 1–3)
- Android + Web Admin project setup.
- Firebase project setup.
- Auth screens: Email + OTP verification + Biometrics setup.
- Firestore data model.

### Phase 2 — Core & Cloud (Weeks 4–7)
- Matching Algorithm **Cloud Function** development.
- Seeker + Giver dashboards.
- Human-to-human chat (text, real-time Firestore listener).
- Blurred photo upload logic.

### Phase 3 — AI & Safety (Weeks 8–10)
- Gemini 3.7 Flash integration (with 50 msg/day quota logic).
- In-app automated Volunteer Training module.
- Content moderation & Crisis detection protocol.
- Admin Panel wiring.

### Phase 4 — Polish & Launch (Weeks 11–13)
- 30-day auto-delete cron job setup.
- Localization (5 languages).
- Play Store listing + release build (AAB, targetSdk 36).
