# BatchIt — App Overview

BatchIt is a WhatsApp-style messaging Android app. It started from the GetStream [whatsapp-clone-compose](https://github.com/GetStream/whatsapp-clone-compose) sample and was rebranded and extended for production use on Firebase Spark + Stream Maker free tiers.

| Item | Value |
|------|--------|
| App name | BatchIt |
| Application ID | `com.batchit.app` |
| Kotlin package (legacy) | `io.getstream.whatsappclone` |
| Firebase project | `batchit-prod` |
| Current version | `1.1.2` (versionCode `8`) |
| Min / target SDK | 24 / 34 |

For local setup, see [SETUP.md](../SETUP.md). For agent/contributor rules, see [AGENTS.md](../AGENTS.md).

---

## What the app does

1. **Sign in** with Google (Firebase Auth), pick a unique username, grant permissions.
2. **Chat** in real time via Stream Chat (channels, messages, search, starred messages).
3. **Call** audio/video via Stream Video (WebRTC), with incoming-call overlay and call history.
4. **Status** — 24-hour text/image/video stories stored in Firestore + Firebase Storage.
5. **Friends** — find users by username or device contact email match.
6. **Settings** — theme, profile, privacy, notifications, storage, blocked contacts, help.
7. **Self-update** — checks Firestore / GitHub Releases, downloads APK, opens system installer.

---

## High-level architecture

```
┌─────────────────────────────────────────────────────────────┐
│  :app  (MainActivity, NavHost, AppUpdate, theme host)       │
└────────────────────────────┬────────────────────────────────┘
                             │
     ┌───────────────────────┼───────────────────────┐
     ▼                       ▼                       ▼
┌──────────┐          ┌──────────┐            ┌──────────┐
│ features │          │   core   │            │ backend  │
│ auth     │          │ design   │            │ Cloudflare│
│ chats    │◄────────►│ nav      │            │ Worker   │
│ status   │          │ model    │            │ Firebase │
│ calls    │          │ network  │            │ Stream   │
│ video    │          │ database │            │          │
│ camera   │          │ data     │            │          │
│ settings │          │ uistate  │            │          │
└──────────┘          └──────────┘            └──────────┘
```

**Pattern:** MVVM + unidirectional data flow (UDF), modularized Clean Architecture.

- **UI:** Jetpack Compose screens + `@HiltViewModel` ViewModels
- **Data:** Repository interfaces/impls over Stream, Firebase, Room, Retrofit
- **DI:** Hilt + KSP across all modules
- **Nav:** Single-activity `NavHost` + tab pager (Camera / Chats / Status / Calls)

---

## Module map

Declared in `settings.gradle.kts`.

### App

| Path | Role |
|------|------|
| `app/.../WhatsApp.kt` | `@HiltAndroidApp` Application |
| `app/.../MainActivity.kt` | Single activity entry |
| `app/.../ui/WhatsAppCloneMain.kt` | Root scaffold + theme |
| `app/.../navigation/WhatsAppNavHost.kt` | Root navigation graph |
| `app/.../update/*` | In-app update state machine |

### Core

| Module | Purpose |
|--------|---------|
| `:core:designsystem` | Theme, colors, typography, shared Compose components |
| `:core:navigation` | `WhatsAppScreens`, `AppComposeNavigator`, nav types |
| `:core:model` | Shared models (`WhatsAppUser`, `CallRecord`, …) |
| `:core:network` | Dispatchers, Retrofit services, network DI |
| `:core:database` | Room DB for call-history users |
| `:core:data` | Repositories bridging DB/network |
| `:core:uistate` | SealedX-generated UI state types |

### Features

| Module | Purpose |
|--------|---------|
| `:features:auth` | Google Sign-In, username, permissions, Stream session |
| `:features:chats` | Channel list, messages, search, starred, friends |
| `:features:status` | Status list, composer, viewer, Firestore repo |
| `:features:calls` | Calls tab + call history UI |
| `:features:video` | Video/audio call UI, incoming overlay |
| `:features:camera` | CameraX capture tab |
| `:features:settings` | Settings hub and sub-screens |

### Other

| Path | Purpose |
|------|---------|
| `workers/stream-token/` | Cloudflare Worker — Firebase ID token → Stream JWT |
| `functions/` | Optional Firebase Cloud Functions (Blaze) |
| `benchmark/` | Macrobenchmark module |
| `build-logic/` | Convention plugins (Compose, Hilt, Spotless) |

---

## How major flows work

### Auth → Stream connect

1. User taps **Continue with Google** → Credential Manager → Google ID token.
2. `AuthRepository.signInWithGoogleIdToken()` → Firebase Auth.
3. Profile upserted to Firestore `users/{uid}`.
4. App `POST`s Firebase ID token to Cloudflare Worker (`STREAM_TOKEN_URL/token`).
5. Worker verifies token, upserts Stream user, returns Stream JWT.
6. `StreamSessionManager` connects `ChatClient` (and Video when needed).
7. Onboarding continues: username → permissions → home.

Demo mode: `BatchItAuthConfig.USE_DEMO_AUTH` (default `false`) for offline Stream-only testing.

### Messaging

- Stream Chat SDK with offline + state plugins (`StreamChatInitializer`).
- Channel list, message thread, pin state, click reactions.
- Chat search and starred messages are first-class screens in navigation.

### Calls

- Stream Video SDK (WebRTC).
- Routes via `WhatsAppScreens.VideoCall` with `call_id`, `video_call`, `members`.
- Incoming call overlay + reactions menu.
- Call history backed by Room (`LocalCallHistoryRepository`).

### Status (24h)

- Metadata in Firestore `statuses/{statusId}` with `expiresAt`, `viewedBy[]`.
- Media in Firebase Storage `statuses/{uid}/{statusId}`.
- Optional Cloud Function `expireStatuses` cleans expired docs (needs Blaze).

### Friends / contacts

- Username lookup via `usernames/{username}` → uid.
- Device contacts scanned; emails matched to Firestore `users` in batches.
- Bidirectional friend docs under `users/{uid}/friends/{friendUid}`.

### In-app updates

1. `AppUpdateRepository` reads Firestore `config/app_update`, else GitHub Releases latest.
2. If remote `versionCode` > installed → show update UI.
3. `DownloadManager` downloads APK; on complete, install via `FileProvider`.
4. `forceUpdate` blocks dismiss. CI: `.github/workflows/release-apk.yml` on `main`.

---

## Navigation routes

Defined in `core/navigation/.../WhatsAppScreens.kt`:

| Screen | Route |
|--------|-------|
| Home (tabs) | `home` |
| Messages | `messages/{channelId}` |
| Call info | `call_info/{user}` |
| Video/audio call | `video_call/{call_id}/{video_call}?members=` |
| Settings | `settings` |
| Privacy / Account / Notifications / Storage / Help | dedicated routes |
| Blocked contacts | `blocked_contacts` |
| Friends | `friends_contacts?mode=chat\|call_audio\|call_video` |
| Chat search | `chat_search` |
| Starred messages | `starred_messages` |

Home tabs (`TopLevelDestination`): **Camera · Chats · Status · Calls**.

---

## Data stores

### Firestore

| Collection / path | Use |
|-------------------|-----|
| `users/{uid}` | Profile (name, username, email, image) |
| `usernames/{username}` | Unique username → uid |
| `users/{uid}/friends/{friendUid}` | Friends |
| `statuses/{statusId}` | Status metadata |
| `config/app_update` | Update metadata |

### Local

| Store | Use |
|-------|-----|
| Room `WhatsAppCloneDataBase` | Cached users for call history |
| SharedPreferences `batchit_auth` | Login flags, username, permissions |
| SharedPreferences `batchit_settings` | Theme, profile name/about |

### Secrets (`secrets.properties`, not committed)

```properties
STREAM_API_KEY=...
STREAM_TOKEN_URL=https://batchit-stream-token.<subdomain>.workers.dev
GOOGLE_WEB_CLIENT_ID=....apps.googleusercontent.com
```

---

## Tech stack (quick reference)

| Concern | Choice |
|---------|--------|
| UI | Jetpack Compose + Material3 |
| DI | Hilt 2.55 + KSP |
| Chat | Stream Chat SDK 6.4.4 |
| Video | Stream Video SDK 1.0.12 |
| Auth | Firebase Auth (Google) |
| DB / storage | Firestore, Storage, Room |
| Push | FCM + Stream Firebase push |
| Images | Landscapist-Glide |
| Network | Retrofit + OkHttp |
| Build | Gradle 8.6, AGP 8.4.1, Kotlin 2.0.10 |

Versions live in `gradle/libs.versions.toml` and `buildSrc/.../Configurations.kt`.

---

## Related docs

| Doc | Contents |
|-----|----------|
| [PROGRESS.md](PROGRESS.md) | What has been built / shipped |
| [DESIGN.md](DESIGN.md) | UI/UX patterns and design system |
| [AGENTS.md](../AGENTS.md) | Rules for AI agents working in this repo |
| [SETUP.md](../SETUP.md) | Environment and Worker setup |
| [PLAY_STORE_CHECKLIST.md](PLAY_STORE_CHECKLIST.md) | Store submission checklist |
| [PRIVACY_POLICY.md](PRIVACY_POLICY.md) | Privacy policy draft |
