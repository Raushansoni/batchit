# BatchIt Privacy Policy

**Last updated:** 2026-07-20  
**App name:** BatchIt  
**Package:** `com.batchit.app`  
**Contact:** privacy@batchit.app

This Privacy Policy describes how BatchIt ("we", "us") collects, uses, and shares information when you use our Android messaging application.

## 1. Information We Collect

### Account information
- Phone number (for authentication)
- Display name and profile photo
- About / status text

### Messages and media
- Text messages, voice notes, images, videos, and files you send
- Status updates (stories) and when they expire
- Call metadata (who called whom, time, duration) — not call audio/video content stored by BatchIt beyond provider processing

### Device and diagnostics
- Device identifiers used for push notifications (FCM tokens)
- Crash logs and analytics (Firebase Crashlytics / Analytics)
- App version and OS version

### Contacts (optional)
- If you grant Contacts permission, we may match phone numbers to find friends on BatchIt. Contact data is hashed where possible and not sold.

## 2. How We Use Information

- Provide messaging, calls, status, and notifications
- Authenticate you and secure your account
- Improve reliability and fix crashes
- Comply with law and enforce our terms

## 3. Service Providers

We use third-party processors:

| Provider | Purpose |
|----------|---------|
| **Stream** (GetStream) | Real-time chat and video/voice calling |
| **Google Firebase** | Phone auth, push (FCM), Cloud Functions, Firestore, Storage, Crashlytics |

Their processing is governed by their own privacy policies. Data is transmitted with encryption in transit (TLS).

## 4. End-to-End Encryption

BatchIt uses Stream’s platform encryption in transit. True WhatsApp-style Signal Protocol E2EE for all message content is not claimed in the current release. Do not send highly sensitive data if your threat model requires client-side E2EE.

## 5. Data Retention

- Messages and media are retained until you delete them or your account is deleted
- Status updates expire after 24 hours
- Account deletion removes your profile, Stream user, and associated cloud data (subject to backup retention windows)

## 6. Your Rights

Depending on your region (including GDPR/CCPA), you may request access, correction, export, or deletion of your personal data via in-app **Settings → Delete account** or by emailing privacy@batchit.app.

## 7. Children

BatchIt is not directed to children under 13 (or the minimum age in your country). We do not knowingly collect data from children.

## 8. Changes

We may update this policy. Continued use after changes constitutes acceptance. The “Last updated” date will change when we publish revisions.

## 9. Contact

Email: privacy@batchit.app

---

Host this document at a public HTTPS URL (e.g. https://batchit.app/privacy) and paste that URL into Google Play Console → App content → Privacy policy.
