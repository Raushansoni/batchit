# BatchIt — Google Play Store Launch Checklist

Use this before submitting `com.batchit.app` to production.

## Developer account
- [ ] Google Play Console account created ($25 fee paid)
- [ ] App created with title **BatchIt** (never use "WhatsApp" in name/screenshots)
- [ ] Category: **Communication**

## Technical
- [ ] `applicationId` is `com.batchit.app`
- [ ] `targetSdk` meets current Play requirement (34+)
- [ ] Release signing configured (Play App Signing)
- [ ] `BatchItAuthConfig.USE_DEMO_AUTH = false`
- [ ] Real `secrets.properties` Stream API key
- [ ] Real `app/google-services.json` from Firebase
- [ ] Cloud Functions deployed with Stream API secret
- [ ] FCM credentials uploaded to Stream Dashboard (provider name `firebase`)
- [ ] ProGuard/R8 rules applied (`app/proguard-rules.pro`)
- [ ] Crashlytics enabled and test crash verified
- [ ] Internal testing track APK/AAB uploaded and installed on 2+ devices

## Store listing
- [ ] Short description (80 chars)
- [ ] Full description
- [ ] App icon 512×512
- [ ] Feature graphic 1024×500
- [ ] Phone screenshots (min 2)
- [ ] Privacy policy URL (host `docs/PRIVACY_POLICY.md`)

## Policy / App content
- [ ] Data safety form completed (see below)
- [ ] Content rating (IARC questionnaire)
- [ ] Target audience / children declaration
- [ ] Ads declaration (typically **No** for BatchIt MVP)
- [ ] News app declaration (No)

## Data safety form — suggested disclosures

| Data type | Collected | Shared | Purpose |
|-----------|-----------|--------|---------|
| Phone number | Yes | Yes (auth/providers) | Account management |
| Name | Yes | Yes (Stream) | App functionality |
| Photos / videos | Yes | Yes (Stream/Firebase) | App functionality |
| Messages | Yes | Yes (Stream) | App functionality |
| Device IDs (FCM) | Yes | Yes (FCM/Stream) | App functionality, fraud prevention |
| Crash logs | Yes | Yes (Firebase) | Analytics |
| Contacts | Optional | No (match only) | App functionality |

- Encryption in transit: **Yes**
- Users can request deletion: **Yes**

## Permissions justification (Play Console)
- Camera — send photos/videos, status
- Microphone — voice notes and calls
- Notifications — message and call alerts
- Contacts — find friends on BatchIt
- Internet — core messaging

## Testing tracks
1. Internal testing
2. Closed testing (invite friends)
3. Open testing (optional)
4. Production rollout (staged 20% → 100%)

## Post-launch
- [ ] Monitor Crashlytics
- [ ] Monitor Stream MAU vs free tier
- [ ] Respond to Play reviews within 3 days
