# Google Play Compliance — Hangar Flow Android

Paste the contents of each section below into the matching Play Console field when you're ready to publish.

## Target SDK
- `compileSdk = 36`, `targetSdk = 36` — ahead of Play's 2025 minimum (35). ✅

## Privacy policy URL
`https://hangarflow.com/privacy`
(Required before the app can be published; host any static page at that URL.)

## Data Safety declarations

Data collected:
| Data type        | Required? | Purpose                          | Shared off-device? |
|------------------|-----------|----------------------------------|--------------------|
| Email address    | Yes       | Account sign-in                  | Yes (Supabase auth) |
| User name        | Yes       | Teammate directory, sign-offs    | Yes (Supabase) |
| Photos           | Optional  | Squawk evidence + work proof     | Yes (Supabase storage) |
| App activity     | Yes       | Time entries, work-log history   | Yes (Supabase) |
| Device/other IDs | Yes       | Per-device realtime event filter | No (in-memory UUID) |

Security practices:
- Data is encrypted in transit (HTTPS).
- Users can request deletion of their account by contacting the shop admin.
- No data is sold to third parties.
- No ads or ad-SDKs.

## Permission rationales
Paste these into the Play Console "Sensitive permissions" section:

- **CAMERA** — "Take photos of squawks and completed work so admins can verify repairs from other devices."
- **READ_MEDIA_IMAGES** — "Attach existing photos from the tablet's gallery to squawks and work logs."
- **POST_NOTIFICATIONS** — "Alert shop techs when a new squawk is filed, a work log is assigned, or a part they requested arrives."
- **INTERNET / ACCESS_NETWORK_STATE** — standard, no disclosure required.

## Third-party SDKs disclosed
- **Supabase** (auth, postgrest, realtime, storage, functions) — handles account, shop data, squawk photos.
- **Cloudflare R2** — manual PDF storage (accessed via Supabase Edge Function, not direct SDK).
- **Firebase Cloud Messaging** *(planned)* — push notifications. Not yet wired.

## Android 15 behavior changes handled
- **Edge-to-edge by default** — `enableEdgeToEdge()` in `MainActivity.onCreate`. ✅
- **Predictive back** — Compose navigation handles back gestures natively. ✅
- **Foreground services** — none used.

## Release build checklist
1. Create keystore: `keytool -genkey -v -keystore hf-release.jks -alias hf -keyalg RSA -keysize 2048 -validity 25000`
2. Add signing config to `app/build.gradle.kts` (release block) — do NOT check the keystore into git.
3. Enable Play App Signing when uploading the first AAB.
4. Build the AAB: `./gradlew :app:bundleRelease`.
5. Upload `app/build/outputs/bundle/release/app-release.aab`.
