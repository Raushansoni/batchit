# BatchIt — Design System & Patterns

How BatchIt looks and how UI/architecture code should be written. Prefer matching existing modules over inventing new patterns.

Related: [APP_OVERVIEW.md](APP_OVERVIEW.md) · [AGENTS.md](../AGENTS.md)

---

## Visual language

BatchIt follows a **WhatsApp-like** green palette (not a generic Material purple theme). Brand should feel familiar to messaging users: green accents, dark teal chrome in dark mode, light gray chat surfaces in light mode.

### Color tokens

Defined in `core/designsystem/.../theme/Color.kt`:

| Token | Hex (approx) | Role |
|-------|----------------|------|
| `GREEN500` / `GREEN450` | `#19887A` / `#1AA05B` | Primary / tab accent |
| `GREEN600` / `GREEN700` | `#0E5E55` / `#00574B` | Darker chrome |
| `DARK_GREEN200` / `300` | `#232D36` / `#101D25` | Dark surfaces / background |
| `LIGHT_BACKGROUND` | `#F0F2F5` | Light app background |
| `LIGHT_CHAT_BACKGROUND` | `#EFE7DE` | Chat wallpaper tone |
| `DARK_SURFACE` | `#1F2C34` | Dark elevated surface |
| `GRAY200` | `#8696A0` | Secondary text / icons |

Use `MaterialTheme.colorScheme` and helpers like `getTabPrimaryColor()` instead of hard-coding one-off colors in feature modules.

### Theme modes

`ThemeMode` enum: `SYSTEM` | `LIGHT` | `DARK`  
Persisted in SharedPreferences `batchit_settings`, applied via `WhatsAppCloneComposeTheme` and `LocalDarkTheme`.

### Typography & motion

- Custom type scale: `core/designsystem/.../theme/Typography.kt`
- Shared animation specs: `Motion.kt`
- Background theming: `BackgroundTheme` / `Background` composable

### Shared components

Prefer these from `:core:designsystem` before building new ones:

| Component | Use |
|-----------|-----|
| `BatchItFab` | Primary floating action |
| `BatchItAvatar` | Circular avatar (Glide via Landscapist) |
| `WhatsAppLoadingIndicator` / `WhatsAppLoadingColumn` | Loading |
| `WhatsAppError` | Error empty/error state |
| `WhatsAppIcons` | Icon set |

---

## UX structure

### Home composition

- **Top bar** — context actions (search, more, etc.) via `WhatsAppTopBar`
- **Four tabs** — Camera · Chats · Status · Calls (`WhatsAppTabPager` + Accompanist pager)
- **Secondary screens** — pushed on the root `NavHost` (messages, settings stack, friends, search, call)

Keep the first home viewport simple: tabs + list content. Dense “dashboard” chrome does not belong here.

### Screen layout conventions

- Scaffold + TopAppBar for pushed screens (settings, search, friends).
- Lists use Stream Compose components where messaging-related; custom lists for settings/status/friends.
- Loading / error / empty handled explicitly (designsystem components or feature-local equivalents).
- Side effects in `LaunchedEffect` / `DisposableEffect`; collect flows with `collectAsStateWithLifecycle()`.

---

## Architecture patterns (must follow)

### Modular boundaries

```
features/*  →  may depend on core/*
core/*      →  must NOT depend on features/* or :app
:app        →  wires features + hosts NavHost / update
```

Do not put feature UI in `core`. Do not reach into another feature’s internal packages; navigate or share via `core:model` / `core:navigation`.

### MVVM + UDF

```
Composable  --events-->  ViewModel  --calls-->  Repository
    ^                       |
    |     StateFlow UI      |
    +------- state ---------+
```

- ViewModels: `@HiltViewModel` + `@Inject constructor`
- Repos: `@Singleton` (or appropriate scope) with interface in data layer when shared
- UI state: sealed types / data classes; SealedX-generated states in `:core:uistate` for message/user/video where already used

### Navigation

- All routes live in `WhatsAppScreens` (`:core:navigation`).
- Cross-module navigation uses `AppComposeNavigator`, not raw `NavController` leaked across modules.
- Add a new screen: define route in `WhatsAppScreens` → register composable in `WhatsAppNavHost` → navigate from feature.

### DI

- Feature `*Module` objects (`AuthModule`, `ChatModule`, `StatusModule`, …)
- Network/DB/dispatchers modules in core
- Prefer constructor injection; avoid service locators

### Async

- Kotlin Coroutines + `Flow` / `StateFlow`
- Dispatchers via `WhatsAppDispatchers` / injected `@Dispatcher(...)` where the project already does
- Prefer repository APIs that return `Flow` for ongoing data

### Compose stability

- `@Immutable` on models passed into Compose when appropriate
- Prefer stable markers already used in the repo (`compose-stable-marker`)
- Avoid unnecessary `remember`/`derivedStateOf` unless measuring a problem; follow existing screen style

---

## Feature UI patterns (by area)

| Area | Pattern |
|------|---------|
| Auth | Multi-step sealed `AuthUiState` (Loading → Google → Username → Permissions → Authenticated) |
| Chats | Stream Compose channel/message lists + BatchIt wrappers (pin, search, starred) |
| Status | Composer → upload Storage → write Firestore; viewer updates `viewedBy` |
| Calls | History list → detail → navigate to `VideoCall` |
| Video | Stream Video call composable + overlay for incoming |
| Settings | Hub list → child screens; theme writes prefs and reapplies theme |
| Updates | Host composable observes `AppUpdateViewModel` state machine; dialog only when Available |

---

## Naming & code style

- Keep package root `io.getstream.whatsappclone.*` unless intentionally migrating packages (large churn).
- User-facing product name is **BatchIt**; prefer `BatchIt*` for new branded UI types (`BatchItFab`, `BatchItSettingsScreen`, `BatchItAuthConfig`).
- Spotless + KtLint are enforced — run format before committing.
- Match file copyright / license headers already present in the module.
- Do not commit `secrets.properties`, keystores, or service-account JSON.

---

## Backend UX constraints (product design)

These shape what the UI may promise:

| Constraint | Design implication |
|------------|--------------------|
| No phone OTP on free tier | Auth copy = Google Sign-In + username, not SMS |
| Sideload updates | Update dialog must explain install permission / unknown apps |
| Status expiry without Blaze CF | UI should treat `expiresAt` as source of truth even if Storage cleanup lags |
| Stream free tier | Avoid implying unlimited history/attachments beyond Stream plan |

---

## Do / don’t (design & UI)

**Do**

- Reuse designsystem colors and components
- Keep WhatsApp-like green identity consistent in light and dark
- One clear primary action per screen (FAB or top-bar action)
- Show explicit loading and error states

**Don’t**

- Introduce a second design language (e.g. purple Material defaults, heavy card grids on home)
- Put settings/stats widgets into the home hero/tab chrome
- Duplicate theme logic inside features — go through settings + designsystem
- Hard-code Stream or Firebase secrets in UI or docs

---

## Where to change design

| Change | Primary location |
|--------|------------------|
| Colors / type / theme | `core/designsystem/.../theme/` |
| Shared widgets | `core/designsystem/.../component/` |
| Icons | `core/designsystem/.../icon/WhatsAppIcons.kt` |
| Home chrome | `app/.../ui/` |
| Feature layouts | `features/<name>/...` |
