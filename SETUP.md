# BatchIt Setup Guide

Complete these steps before running the production app.

## 1. Stream Chat + Video

1. Sign up at https://dashboard.getstream.io
2. Create an app (Chat + Video enabled)
3. Copy the **API Key** into `secrets.properties`:

```properties
STREAM_API_KEY=your_real_api_key_here
```

4. Copy the **API Secret** — use it only in Cloud Functions (never in the Android app)
5. Optional: apply for a [Maker Account](https://getstream.io/maker-account/) (up to 2,000 MAU free)

## 2. Firebase

1. Create a project at https://console.firebase.google.com named `batchit-prod`
2. Add an Android app with package name: `com.batchit.app`
3. Download `google-services.json` into `app/google-services.json` (replace the placeholder)
4. Enable **Phone Authentication** (Authentication → Sign-in method)
5. Enable **Cloud Firestore**, **Cloud Storage**, and **Cloud Functions** (Blaze plan required for Functions)
6. Enable **Cloud Messaging** for push notifications
7. Upload the Firebase service-account JSON to the Stream Dashboard (Chat → Push → Firebase)

## 3. Cloud Functions (Stream token server)

```bash
cd functions
npm install
firebase login
firebase use batchit-prod
# Set secrets:
firebase functions:config:set stream.key="YOUR_API_KEY" stream.secret="YOUR_API_SECRET"
# Or use modern secrets:
# firebase functions:secrets:set STREAM_API_KEY
# firebase functions:secrets:set STREAM_API_SECRET
firebase deploy --only functions
```

## 4. Google Play Console

1. Pay the one-time $25 developer fee at https://play.google.com/console
2. Create app listing **BatchIt** (Communication category)
3. Follow `docs/PLAY_STORE_CHECKLIST.md`

## 5. Run locally

1. Open the project in Android Studio (Ladybug+)
2. Sync Gradle
3. Run on an emulator (API 26+) or physical device
4. Demo mode: until Firebase Phone Auth is configured, the app can use Stream `devToken` via `BatchItAuthConfig.USE_DEMO_AUTH`

## Demo vs Production

| Mode | When | Auth |
|------|------|------|
| Demo | `USE_DEMO_AUTH = true` | Hard-coded Stream user + `devToken` |
| Production | `USE_DEMO_AUTH = false` + Firebase configured | Phone OTP → Cloud Function token |
