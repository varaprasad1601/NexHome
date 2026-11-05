# NexHome partner app

The partner app (Android first) in React Native with Expo (SDK 58) and Expo Router, plain JavaScript, in the same
design direction A, "Clean & Bright", as the customer app (`android/nexhome-customer`): DM Sans, navy / blue / green
on a light ground, white cards, the 4-tab white bottom bar. It talks to the NexHome backend (`backend/nexhome`).

## Run it

1. Start the backend (`backend/nexhome`, dev profile) on port 8080.
2. The app finds the backend by itself: the computer running `npx expo start`, port 8080 (`src/api/client.js`), so
   any phone or emulator on the same Wi-Fi works without a `.env`. Allow port 8080 through the Windows firewall once
   (PowerShell **as administrator**:
   `New-NetFirewallRule -DisplayName "NexHome backend 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private`),
   and keep the Wi-Fi network *Private*. Set `EXPO_PUBLIC_API_BASE_URL` in `.env` only for a fixed server (see
   `.env.example`). `npx expo start --tunnel` cannot reach the backend this way.
3. `npm install`, then `npx expo start` and press `a` (emulator) or scan the QR code with **Expo Go** on the phone.

Sign-in codes: until the SMS provider (MSG91) is configured, the backend logs every OTP (dev profile:
`OTP <id> for +91 <number> (LOGIN): 123456`).

## Build an APK

A release APK has no Metro server to find the computer, so put the backend in `.env` first: `EXPO_PUBLIC_API_BASE_URL=http://<this computer's Wi-Fi IP>:8080`. Then (Android Studio's SDK in `ANDROID_HOME`, its JDK as `JAVA_HOME`):

```bash
CI=1 npx expo prebuild --platform android --clean
cd android && ./gradlew assembleRelease
```

The APK is `android/app/build/outputs/apk/release/app-release.apk` (signed with the debug key: fine for testing, not for the Play Store). If the computer's IP changes, change `.env` and build again (or reserve the IP in the router).

## Push notifications

Removed for now: Expo Go on Android cannot load `expo-notifications`. The backend still supports push (`/api/notifications/devices/register`), but this app does not register the phone.

## What it does

- **Sign in** with the mobile number and a code (`/api/auth/otp/request`, `/otp/verify`); a new number signs up as a
  partner (`/api/auth/register`, role PARTNER, which creates an empty NOT_STARTED partner profile). An account without
  an active PARTNER role is told to use the customer app or contact support. Tokens are kept in `expo-secure-store`
  and renewed on a 401 (`context/AuthProvider`, which also loads `/api/partners/profiles/me`).
- **Where it opens** (`app/index.js`) follows the onboarding status: NOT_STARTED / DRAFT / CHANGES_REQUESTED the
  onboarding, UNDER_REVIEW / UPDATE_REQUESTED / REJECTED the waiting screen, APPROVED the tabs.
- **Onboarding** (`app/onboarding/`): an overview with the 8 steps, a tick for each one complete and the share done,
  the staff's reason when changes were requested, and **Submit for review** (`PUT /api/partners/profiles/submit`;
  the backend's "Complete these fields first" message is shown in words). Each step saves on its own
  (`PUT /api/partners/profiles/update`) and opens the next, so a partner can stop and come back:
  personal (names, Aadhaar, 18 or older, language, alternate number), address, work (skill category, experience, main
  service category, other categories, arrangement, payment basis, expected earnings, visit charge, minimum job
  payment), services offered (`/api/partners/services/*`, one main), areas and weekly hours (service areas, travel km,
  weekends, the day × AVAILABILITY_SLOT grid; weekends must agree with the grid), emergency contact, documents
  (government ID with its type, number and a scan or photo uploaded first through `/api/files/upload`, purpose
  PARTNER_DOCUMENT; skill certificates) and bank account (IFSC filled from Razorpay's public IFSC service when it
  answers). Lookups come from `/api/lookups/values/{TYPE}` (`hooks/useLookup`).
- **Waiting screens** (`app/status.js`): under review, changes being verified (with a way into jobs and earnings),
  rejected with the reason; pull to refresh.
- **Home**: Online / Offline switch (`/api/partners/profiles/availability/update`), new offers with Accept / Decline
  (`/api/bookings/offers/me`, `/assignments/respond`), jobs assigned and waiting for an answer, today's jobs, rating,
  jobs done and earnings.
- **Jobs**: Upcoming / Completed (`/api/bookings/partner/me`). The job page: customer with Call and Open in Maps,
  details, payment, history, and by state Accept / Decline, Start work, Pause, Resume, Finish work
  (`POST /api/bookings/timer`), the time worked, Cash collected for a cash job, Get help.
- **Earnings**: Today / This week / This month / Overall (`/api/payouts/earnings/{id}?period=`) with the jobs, total,
  commission and earnings and every job; Payouts (`/api/payouts/me`).
- **Profile**: photo (upload, PROFILE_PHOTO), name, partner id, rating, status; services and working hours (read
  only after submitting), time off (add, cancel), documents and bank accounts (an approved partner's change sends the
  profile for verification again: UPDATE_REQUESTED, warned before saving), reviews, notifications, support (tickets
  with requesterRole PARTNER, call, WhatsApp), terms and policies (PARTNER; new versions to accept open after
  sign-in), log out.

## Layout

- `src/app/`: the screens (Expo Router: every file is a route; `(tabs)/` is the tab bar, `onboarding/` the steps).
- `src/components/ui.js`: Text (DM Sans), Button, Card, Tag, Pick, Choices, Check, Field, Header, Banner, Avatar,
  Tabs, Row, Cover / GradientCover, Empty, ...; `StepScreen` (an onboarding step), `WeeklyGrid`, `JobCard`,
  `OfferCard`, `DocumentsList`, `BankAccountsList`, `Progress`.
- `src/api/client.js` (`apiRequest` with JSON or multipart, `fileUrl`, `uploadFile`, cached `lookupValues`),
  `src/context/AuthProvider.js`, `src/hooks/useApi.js` (loads on focus), `src/hooks/useLookup.js`, `src/utils/`
  (money and the backend's "Thu, 06 Nov 2026 10:30:00 IST" dates; status labels; the onboarding steps and checks),
  `src/theme.js`.

## Checks

`npx expo lint`, and `npx expo export --platform android` to bundle it.
