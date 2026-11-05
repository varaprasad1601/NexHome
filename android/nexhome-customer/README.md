# NexHome customer app

The customer app (Android first) in React Native with Expo (SDK 58) and Expo Router, plain JavaScript, in design
direction A, "Clean & Bright" (`Admin Sreens/customer-app-samples/customer-app-designs.html`). It talks to the
NexHome backend (`backend/nexhome`).

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
  customer (`/api/auth/register`, role CUSTOMER) and then gives its name. An account without the CUSTOMER role is
  told to use the right app. Tokens are kept in `expo-secure-store` and renewed on a 401 (`context/AuthProvider`).
- **Policies**: new versions to accept open after sign-in (`/api/policies/pending`, `/accept`); Profile > Terms &
  policies shows the current ones.
- **Home**: greeting and default address, notifications bell (unread count), search, banner, every category, popular
  services; a cart bar while the cart has services.
- **Services**: by category or search; the service page with images, options (packages / home sizes) priced by the
  backend's quote, what to keep ready, FAQs, Add to cart.
- **Checkout** (one page): services, address, as soon as possible or a day and half-hour slot, cash or online.
  Online payment has no gateway yet (the backend's MANUAL provider): the booking gets its payment order and staff
  record the payment.
- **Bookings**: Upcoming / Past with status, progress and the professional; the booking page with price, payment,
  history, Cancel (with a reason), Rate (service and professional), "I paid the professional" for cash, Get help.
- **Support**: call / WhatsApp (General Settings' numbers), tickets (new, conversation, replies).
- **Notifications**, **addresses** (add, edit, default, delete), **profile** (name, email), log out.

## Layout

- `src/app/`: the screens (Expo Router: every file is a route; `(tabs)/` is the tab bar).
- `src/components/ui.js`: Text (DM Sans), Button, Card, Tag, Pick, Field, Header, Cover / GradientCover, Empty, ...
- `src/api/client.js` (`apiRequest`, `fileUrl`), `src/context/AuthProvider.js`, `src/hooks/useApi.js` (loads on
  focus), `src/utils/` (money and the backend's "Thu, 06 Nov 2026 10:30:00 IST" dates; status labels), `src/theme.js`.

## Checks

`npx expo lint`, and `npx expo export --platform android` to bundle it.
