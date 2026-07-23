# BatchIt Setup Guide

## Free production (default — no Firebase billing)

Stack:

- **Auth:** Google Sign-In (Spark / free) + username after first login
- **Tokens:** Cloudflare Worker (`workers/stream-token`)
- **Chat:** Stream Chat + Video (Maker free tier)

### 1. Stream API key

Create an app at https://dashboard.getstream.io and put the **API key** in root `secrets.properties`:

```properties
STREAM_API_KEY=your_stream_api_key
STREAM_TOKEN_URL=https://batchit-stream-token.<your-subdomain>.workers.dev
GOOGLE_WEB_CLIENT_ID=your_web_client_id.apps.googleusercontent.com
```

Keep the **API secret** for the Worker only (never put secrets in this markdown file).

### 2. Firebase (already linked)

- Project: `batchit-prod`
- Android package: `com.batchit.app`
- File: `app/google-services.json`
- Google Sign-In: enabled (and Email/Password kept as fallback)

Confirm in Console:  
https://console.firebase.google.com/project/batchit-prod/authentication/providers

#### Google Web Client ID

1. Open Firebase Console → Project settings → Your apps → Web app  
   **or** Google Cloud Console → APIs & Services → Credentials
2. Copy the **Web client** OAuth 2.0 Client ID (`….apps.googleusercontent.com`)
3. Paste it as `GOOGLE_WEB_CLIENT_ID` in `secrets.properties`

Also re-download `app/google-services.json` after enabling Google Sign-In / adding SHA-1 so OAuth clients are present.

Debug SHA-1 is already registered for this machine’s debug keystore.

### 3. Deploy the free token Worker

```bash
cd workers/stream-token
npm install
npx wrangler login
npx wrangler secret put STREAM_API_KEY
npx wrangler secret put STREAM_API_SECRET
npx wrangler secret put FIREBASE_PROJECT_ID
# when prompted for FIREBASE_PROJECT_ID, enter: batchit-prod
npx wrangler deploy
```

Each `secret put` command asks you to paste the value interactively.  
Paste the printed Worker URL into `STREAM_TOKEN_URL` in `secrets.properties` (no trailing slash).

### 4. Run the Android app

1. Sync Gradle → Run
2. Tap **Continue with Google**
3. Pick a unique username (3–20 chars)

`BatchItAuthConfig.USE_DEMO_AUTH` is `false`. For offline Stream-only testing, set it back to `true` temporarily.

---

## In-app updates (free, GitHub-driven)

On every push to `main`, [`.github/workflows/release-apk.yml`](.github/workflows/release-apk.yml):

1. Builds a debug APK
2. Publishes a GitHub Release (`v{versionName}+{versionCode}`) with the APK + `app_update.json`
3. Optionally writes Firestore `config/app_update` when secret `FIREBASE_SERVICE_ACCOUNT` is set

The app checks Firestore first, then falls back to GitHub Releases API. If `versionCode` is newer than the installed build, it shows an **Update** dialog, downloads the APK, and opens the system installer.

### One-time setup

1. Bump `versionCode` / `patchVersion` in `buildSrc/src/main/kotlin/Configurations.kt` before shipping.
2. Deploy Firestore rules from `firestore.rules` (Console or `firebase deploy --only firestore:rules`).
3. (Recommended) Add GitHub repo secret `FIREBASE_SERVICE_ACCOUNT` = JSON for a Firebase Admin service account with Firestore write access.
4. Users must allow **Install unknown apps** for BatchIt (sideload installs).

Without the Firebase secret, updates still work via public GitHub Release assets.

---

## What this does not include (needs Blaze / paid)

| Feature | Why |
|---------|-----|
| Phone OTP | SMS billing |
| Firebase Cloud Functions | Blaze plan |

---

## Optional: Blaze / Phone later

See older `functions/` folder if you upgrade to Blaze and want Phone OTP + Callable Functions instead of the Worker.
