# AGENTS.md — BatchIt

Instructions for AI coding agents (and humans) working in this repository.

Product docs: [docs/APP_OVERVIEW.md](docs/APP_OVERVIEW.md) · Progress: [docs/PROGRESS.md](docs/PROGRESS.md) · Design: [docs/DESIGN.md](docs/DESIGN.md) · Setup: [SETUP.md](SETUP.md)

---

## What this project is

**BatchIt** is a WhatsApp-style Android messenger:

- Application ID: `com.batchit.app`
- Kotlin packages mostly still: `io.getstream.whatsappclone.*` (upstream heritage)
- Firebase project: `batchit-prod` (Spark / free-oriented)
- Chat & calls: **Stream** Chat + Video SDKs
- Stream JWTs: **Cloudflare Worker** at `workers/stream-token` (not Blaze Functions by default)

Prefer free-tier-safe solutions. Do not add Phone OTP, paid Cloud Functions, or other Blaze-only paths unless the user explicitly asks.

---

## Repo map (where to edit)

| Goal | Start here |
|------|------------|
| App shell / NavHost / update | `app/src/main/kotlin/io/getstream/whatsappclone/` |
| Theme & shared UI | `core/designsystem/` |
| Routes / navigator | `core/navigation/` |
| Auth / Google / username | `features/auth/` |
| Chat / search / friends / starred | `features/chats/` |
| Status | `features/status/` |
| Call history UI | `features/calls/` |
| Video/audio call UI | `features/video/` |
| Camera tab | `features/camera/` |
| Settings | `features/settings/` |
| Versions | `buildSrc/src/main/kotlin/Configurations.kt` |
| Dependency versions | `gradle/libs.versions.toml` |
| Token Worker | `workers/stream-token/` |
| Optional Cloud Functions | `functions/` |

---

## Non-negotiable rules

1. **Secrets stay out of git** — Never commit `secrets.properties`, keystores, `google-services.json` replacements with production secrets in docs, or Firebase service-account JSON. Read keys from existing `secrets.properties` / CI secrets only.
2. **Module boundaries** — `features/*` → `core/*` only. `core` must not depend on `features` or `app`. Shared models/routes go in `core:model` / `core:navigation`.
3. **Navigation** — New screens: add `WhatsAppScreens` entry → register in `WhatsAppNavHost` → navigate via `AppComposeNavigator` when crossing modules.
4. **DI** — Use Hilt (`@HiltViewModel`, `@Inject`, feature `*Module`). Do not introduce a second DI framework.
5. **UI stack** — Jetpack Compose + existing designsystem. Match WhatsApp-like greens; see [docs/DESIGN.md](docs/DESIGN.md).
6. **Scope discipline** — Change only what the task needs. No drive-by refactors, unrelated file cleanup, or package renames of `io.getstream.whatsappclone` unless requested.
7. **Commits / PRs** — Only when the user asks. Follow their git/PR rules (no force-push to main, no `--no-verify`, no amending others’ commits).
8. **Do not rewrite README** to remove upstream attribution without being asked; BatchIt-specific setup lives in `SETUP.md` and `docs/`.

---

## Patterns to copy

### Feature screen

- Composable in `features/<feature>/...`
- State in `@HiltViewModel` as `StateFlow`
- Collect with `collectAsStateWithLifecycle()`
- Loading / error / success sealed UI state when the flow is multi-step

### Repository

- Talk to Stream / Firebase / Room / Retrofit inside the repository (or a small client already used there)
- Expose suspend functions or `Flow` to ViewModels
- Keep Firestore paths consistent with existing collections (`users`, `usernames`, `statuses`, `config/app_update`)

### Auth & Stream

- Production path: Firebase Google Sign-In → Worker `/token` → `StreamSessionManager`
- Do not put `STREAM_API_SECRET` in the Android app
- Leave `BatchItAuthConfig.USE_DEMO_AUTH = false` unless the user wants demo mode for local testing

### In-app updates

- Logic lives under `app/.../update/`
- Version bumps: `Configurations.versionCode` **and** version name fields together when shipping
- Prefer Firestore `config/app_update` + GitHub Releases fallback already implemented

---

## Build & verify

```bash
# Sync / build (Windows: use gradlew.bat)
./gradlew :app:assembleDebug

# Spotless (formatting)
./gradlew spotlessApply
```

- Min SDK 24, target 34 — do not raise without discussion.
- Release builds enable minify/shrink — keep ProGuard rules in mind for reflection-heavy SDKs.

---

## Common tasks (checklist)

### Install debug APK on phones

- Skill: `.agents/skills/install-debug` (invoke with `/install-debug` or ask to install)
- Script: `.agents/skills/install-debug/scripts/install-debug.sh --build`
- Wireless endpoints: copy `.cursor/devices.local.example` → `.cursor/devices.local`
- Auto after agent `assembleDebug`: `.cursor/hooks.json` → install-only when devices are online

### Add a settings sub-screen

1. Composable + ViewModel (if needed) in `features/settings`
2. Route on `WhatsAppScreens`
3. Composable destination in `WhatsAppNavHost`
4. Entry row from `BatchItSettingsScreen`

### Add a Firestore field

1. Update repository read/write
2. Update security rules in `firestore.rules` if access changes
3. Document in `docs/APP_OVERVIEW.md` data section if it is a new collection

### Ship a new APK version

1. Bump `Configurations.kt` (`versionCode` + patch/minor as appropriate)
2. Push to `main` (triggers release workflow) **only if user asks to push**
3. Note the release in `docs/PROGRESS.md`

---

## Explicitly out of scope (unless user asks)

- Phone OTP / SMS auth
- Migrating all packages from `io.getstream.whatsappclone` → `com.batchit`
- Replacing Stream with a custom chat backend
- Play Billing / payments
- Large README redesign or removing GetStream sample heritage

---

## When stuck

1. Read [docs/APP_OVERVIEW.md](docs/APP_OVERVIEW.md) for flow diagrams and module roles.
2. Mirror the closest existing feature (auth multi-step, settings child screen, status repo).
3. Check [SETUP.md](SETUP.md) for Worker / secrets / update pipeline issues.
4. Ask the user before changing billing tier assumptions or deleting upstream-compatible APIs.
