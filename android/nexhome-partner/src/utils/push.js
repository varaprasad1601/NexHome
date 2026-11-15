import Constants, { ExecutionEnvironment } from 'expo-constants';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

/**
 * Push notifications (FCM, backend V53): the phone's token is registered after sign-in
 * (POST /api/notifications/devices/register) and unregistered at log-out, so a notification makes a sound and shows
 * in the phone's tray while the app is in the background or closed, and pops up as a banner while it is open. The
 * backend sends HIGH ones on the `nexhome_alerts` channel, the partner's arrival (PARTNER_ARRIVED) on
 * `nexhome_doorbell` and the rest on `nexhome_general` (`CHANNELS`); the doorbell plays `assets/sounds/doorbell.wav`,
 * the others `assets/sounds/notify.wav`. The notification permission is asked on the first launch
 * (`askOnFirstLaunch`) and again at sign-in if not allowed.
 *
 * Only in a build (EAS APK / AAB): Expo Go cannot receive these and throws as soon as expo-notifications loads, so the
 * module is required lazily and never in Expo Go (the app then shows its own popup and chime: LiveWatcher).
 */
export const PUSH_AVAILABLE =
  Platform.OS === 'android' && Constants.executionEnvironment !== ExecutionEnvironment.StoreClient;

let Notifications = null;
function notifications() {
  if (!PUSH_AVAILABLE) return null;
  if (!Notifications) {
    try {
      Notifications = require('expo-notifications');
    } catch {
      return null;
    }
  }
  return Notifications;
}

/** The notification channels; the backend's pushes name the same ids (NexHomeFcmPushSender). */
export const CHANNELS = { general: 'nexhome_general', alerts: 'nexhome_alerts', doorbell: 'nexhome_doorbell' };
/** Channel ids of older builds: removed. */
const OLD_CHANNELS = ['default', 'alerts', 'doorbell'];
const ASKED_KEY = 'nexhome.notificationPermissionAsked';

/**
 * The first time the app runs (once per install), it asks to show notifications (Android 13+), before sign-in, so
 * popups and the notification bar work from the start. Signing in asks again if it was not allowed then.
 */
async function askOnFirstLaunch(N) {
  try {
    if (await SecureStore.getItemAsync(ASKED_KEY)) return;
    await SecureStore.setItemAsync(ASKED_KEY, '1');
    const { status, canAskAgain } = await N.getPermissionsAsync();
    if (status !== 'granted' && canAskAgain !== false) await N.requestPermissionsAsync();
  } catch {
    // Asked again at sign-in (registerPush)
  }
}

let token = null;
let lastPushAt = 0;
let registered = false;

/** Whether this phone is registered for push: every notification then arrives as the phone's own banner. */
export function pushReady() {
  return registered;
}

/** When the last push came in while the app was open (its banner and sound already showed). */
export function lastPushTime() {
  return lastPushAt;
}

/**
 * Channels, the foreground behaviour and the listeners; returns a cleanup. `onPush(data)` runs when a push arrives
 * while the app is open, `onOpen(data)` when the person taps one (also the tap that started the app).
 *
 * As in commit b9c0ec4 (the last one whose popups worked): every push shows its banner with its sound while the app
 * is open, nothing is checked first. (Later builds hid a push already "shown": the received listener marked it shown
 * before the handler ran, so the handler hid every banner; and `shouldPlaySound: false` made expo post it silent,
 * which Android never pops up.)
 */
export function setUpPush({ onPush, onOpen }) {
  const N = notifications();
  if (!N) return () => {};
  N.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: false
    })
  });
  // The channels at HIGH / MAX importance, so banners pop up; the ids of older builds are removed. Then, on the first
  // launch, the permission is asked.
  const channel = { sound: 'notify.wav', vibrationPattern: [0, 250, 150, 250], lightColor: '#0b438b' };
  Promise.all([
    N.setNotificationChannelAsync(CHANNELS.general, { ...channel, name: 'Notifications', importance: N.AndroidImportance.HIGH }),
    N.setNotificationChannelAsync(CHANNELS.alerts, { ...channel, name: 'Urgent alerts', importance: N.AndroidImportance.MAX }),
    // The partner reaching the door (PARTNER_ARRIVED) rings the doorbell, also when the app is closed
    N.setNotificationChannelAsync(CHANNELS.doorbell, { ...channel, sound: 'doorbell.wav', name: 'Professional at the door', importance: N.AndroidImportance.MAX })
  ])
    .catch(() => {})
    .then(() => Promise.all(OLD_CHANNELS.map((id) => N.deleteNotificationChannelAsync(id).catch(() => {}))))
    .then(() => askOnFirstLaunch(N));

  const received = N.addNotificationReceivedListener((notification) => {
    lastPushAt = Date.now();
    onPush?.(notification?.request?.content?.data ?? {});
  });
  const opened = N.addNotificationResponseReceivedListener((response) => {
    onOpen?.(response?.notification?.request?.content?.data ?? {});
  });
  N.getLastNotificationResponseAsync?.()
    .then((response) => {
      if (response) onOpen?.(response.notification?.request?.content?.data ?? {});
    })
    .catch(() => {});
  return () => {
    received.remove();
    opened.remove();
  };
}

/** Asks for permission (Android 13+) and registers this phone for the signed-in account and `app`. */
export async function registerPush(authRequest, app) {
  const N = notifications();
  if (!N) return false;
  try {
    let { status } = await N.getPermissionsAsync();
    if (status !== 'granted') ({ status } = await N.requestPermissionsAsync());
    if (status !== 'granted') return false;
    const device = await N.getDevicePushTokenAsync();
    if (!device?.data) return false;
    await authRequest('/api/notifications/devices/register', {
      method: 'POST',
      body: { deviceToken: device.data, app, platform: 'ANDROID' }
    });
    token = device.data;
    registered = true;
    return true;
  } catch {
    // No Firebase in this build, no network, ...: the app's own popup and chime still work
    return false;
  }
}

/** At log-out, before the session ends: this phone stops getting the account's pushes. */
export async function unregisterPush(authRequest) {
  if (!token) return;
  const deviceToken = token;
  token = null;
  registered = false;
  await authRequest('/api/notifications/devices/unregister', { method: 'PUT', body: { deviceToken } }).catch(() => {});
}
