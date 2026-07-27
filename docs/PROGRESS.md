# BatchIt — Progress

Living changelog of what BatchIt has shipped beyond the upstream GetStream WhatsApp clone. Update this file when a meaningful feature or milestone lands.

**Current release:** `1.1.17` (versionCode `23`) — July 2026  
**Firebase:** `batchit-prod` · **Package:** `com.batchit.app`

---

## Status legend

| Mark | Meaning |
|------|---------|
| Done | Shipped in current mainline |
| Partial | Works with known limits |
| Planned | Not started / blocked |

---

## Milestone summary

### Foundation (upstream + rebrand)

| Item | Status | Notes |
|------|--------|-------|
| Jetpack Compose WhatsApp UI shell | Done | Tabs: Camera, Chats, Status, Calls |
| Stream Chat messaging | Done | Offline + state plugins |
| Stream Video calls | Done | Audio + video WebRTC |
| Multi-module architecture | Done | `app` + `core:*` + `features:*` |
| Hilt DI + App Startup | Done | Initializers for Chat/Video/logs |
| Rebrand to BatchIt / `com.batchit.app` | Done | Firebase `batchit-prod` linked |
| Google Sign-In (no phone OTP) | Done | Spark-friendly; avoids SMS billing |
| Unique username onboarding | Done | Firestore `usernames` transaction |
| Permissions onboarding step | Done | After username setup |
| Cloudflare Worker Stream tokens | Done | Replaces Blaze Cloud Functions for tokens |
| Demo auth toggle | Done | `BatchItAuthConfig.USE_DEMO_AUTH` |

### WhatsApp-parity features (free tier)

| Item | Status | Notes |
|------|--------|-------|
| Ringing / incoming call overlay | Done | `IncomingCallOverlay` |
| Camera tab (CameraX) | Done | `:features:camera` |
| Settings hub + sub-screens | Done | Theme, account, privacy, notifications, storage, help |
| Theme mode (System / Light / Dark) | Done | Persisted in `batchit_settings` |
| In-app chat search | Done | `ChatSearchScreen` |
| Starred messages | Done | Screen + repository |
| Friends / contacts invite | Done | Username + email contact match |
| Blocked contacts UI | Done | Settings path |
| Account profile edit | Done | Display name + about |
| Sign out / delete account | Done | Cascades Firestore + Auth (Stream/Storage as implemented) |
| 24h Status (text / image / video) | Done | Firestore + Storage; expire CF needs Blaze |
| Call history | Done | Room-backed |

### Distribution & ops

| Item | Status | Notes |
|------|--------|-------|
| In-app update (GitHub + Firestore) | Done | DownloadManager + FileProvider install |
| Release workflow on `main` | Done | `.github/workflows/release-apk.yml` |
| `app_update.json` on GitHub Release | Done | Fallback when Firestore secret missing |
| Firestore rules for `config/app_update` | Done | See `firestore.rules` |
| Setup guide | Done | [SETUP.md](../SETUP.md) |
| Play Store checklist | Done | [PLAY_STORE_CHECKLIST.md](PLAY_STORE_CHECKLIST.md) |
| Privacy policy draft | Done | [PRIVACY_POLICY.md](PRIVACY_POLICY.md) |
| Project docs suite | Done | This file + APP_OVERVIEW + DESIGN + AGENTS |

---

## Version history (BatchIt)

| Version | Code | Highlights |
|---------|------|------------|
| 1.1.6 | 12 | Video FCM push wiring + call join/permission fixes |
| 1.1.5 | 11 | Signed release publish bump |
| 1.1.4 | 10 | Settings “Check for updates” |
| 1.1.3 | 9 | Call/message notifications + deep links |
| 1.1.2 | 8 | Bump so installed clients can detect an available update |
| 1.1.x | — | Free in-app updates via GitHub Releases + Firestore |
| 1.0 / earlier | — | Free-tier parity: ringing, camera, settings, chat search; auth + Worker |

See `git log` for exact commit messages (`eef2c34`, `935877f`, `21f7d36`, …).

---

## Intentionally not included (yet)

These need Blaze billing, Play Console, or extra product work:

| Item | Why blocked / deferred |
|------|------------------------|
| Phone OTP auth | SMS costs; Google Sign-In is the free path |
| Firebase Cloud Functions in production | Needs Blaze; Worker covers tokens |
| Scheduled `expireStatuses` in prod | Same Blaze requirement; client still sets `expiresAt` |
| Play Store listing / Play In-App Updates API | Sideload + custom updater used instead |
| End-to-end encrypted messaging | Relies on Stream product features / future work |
| Group admin tools, broadcast lists, payments | Out of current scope |

---

## Suggested next work (backlog)

Use this as a short backlog; reorder as priorities change.

1. **Polish status expiry** without Blaze (client-side hide of expired docs; periodic Storage cleanup script).
2. **Play Store package** when ready — follow `PLAY_STORE_CHECKLIST.md`; consider Play In-App Updates alongside or instead of sideload.
3. **Push notification QA** across OEMs (FCM + Stream device registration).
4. **Friends UX** — empty states, invite share link, clearer call-vs-chat modes.
5. **ProGuard / R8 audit** on release builds with Stream + Firebase.
6. **Replace leftover GetStream branding** in README/UI strings where still visible.
7. **Automated UI tests** for auth → username → home and update dialog paths.

---

## How to update this file

When you finish a feature:

1. Move or add a row under the right milestone table → **Done** (or **Partial** with a note).
2. Bump the “Current release” line if you also bumped `Configurations.kt`.
3. Add a one-line entry under **Version history** if user-visible.
4. Remove or rewrite backlog items that are done.
