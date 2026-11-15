import Constants, { ExecutionEnvironment } from 'expo-constants';
import { Platform } from 'react-native';

/**
 * Push notifications (FCM, backend V53): the phone's token is registered after sign-in
 * (POST /api/notifications/devices/register) and unregistered at log-out, so a notification makes a sound and shows
 * in the phone's tray while the app is in the background or closed. The backend sends HIGH ones on the `alerts`
 * channel and the rest on `default`; both channels play `assets/sounds/notify.wav`.
 *
 * Only in a build (EAS APK / AAB): Expo Go cannot receive these and throws as soon as expo-notifications loads, so the
 * module is required lazily and never in Expo Go (the app then still chimes in the foreground: LiveWatcher).
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

let token = null;
let lastPushAt = 0;

/** When the last push came in while the app was open (its sound already played). */
export function lastPushTime() {
  return lastPushAt;
}

/**
 * Channels, the foreground behaviour and the listeners; returns a cleanup. `onPush()` runs when a push arrives while
 * the app is open, `onOpen(data)` when the person taps one (also the tap that started the app).
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
  const channel = { sound: 'notify.wav', vibrationPattern: [0, 250, 150, 250], lightColor: '#0b438b' };
  N.setNotificationChannelAsync('default', { ...channel, name: 'Notifications', importance: N.AndroidImportance.HIGH }).catch(() => {});
  N.setNotificationChannelAsync('alerts', { ...channel, name: 'Urgent alerts', importance: N.AndroidImportance.MAX }).catch(() => {});

  const received = N.addNotificationReceivedListener(() => {
    lastPushAt = Date.now();
    onPush?.();
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
    return true;
  } catch {
    // No Firebase in this build, no network, ...: the foreground chime still works
    return false;
  }
}

/** At log-out, before the session ends: this phone stops getting the account's pushes. */
export async function unregisterPush(authRequest) {
  if (!token) return;
  const deviceToken = token;
  token = null;
  await authRequest('/api/notifications/devices/unregister', { method: 'PUT', body: { deviceToken } }).catch(() => {});
}
