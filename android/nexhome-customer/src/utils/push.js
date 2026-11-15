import Constants, { ExecutionEnvironment } from 'expo-constants';
import { Platform } from 'react-native';

/**
 * Push notifications (FCM, backend V53): the phone's token is registered after sign-in
 * (POST /api/notifications/devices/register) and unregistered at log-out, so a notification makes a sound and shows
 * in the phone's tray while the app is in the background or closed. The backend sends HIGH ones on the `alerts`
 * channel, the partner's arrival (PARTNER_ARRIVED) on `doorbell` and the rest on `default`; `doorbell` plays
 * `assets/sounds/doorbell.wav`, the others `assets/sounds/notify.wav`.
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
  // While the app is open the banner shows and LiveWatcher plays the NexHome chime itself (`onPush`), so the sound is
  // the same on every screen whatever the phone's channel settings; in the background the channel's sound plays
  N.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: false,
      shouldSetBadge: false
    })
  });
  const channel = { sound: 'notify.wav', vibrationPattern: [0, 250, 150, 250], lightColor: '#0b438b' };
  N.setNotificationChannelAsync('default', { ...channel, name: 'Notifications', importance: N.AndroidImportance.HIGH }).catch(() => {});
  N.setNotificationChannelAsync('alerts', { ...channel, name: 'Urgent alerts', importance: N.AndroidImportance.MAX }).catch(() => {});
  // The partner reaching the door (PARTNER_ARRIVED) rings the doorbell, also when the app is closed
  N.setNotificationChannelAsync('doorbell', { ...channel, sound: 'doorbell.wav', name: 'Professional at the door', importance: N.AndroidImportance.MAX }).catch(() => {});

  const received = N.addNotificationReceivedListener((notification) => {
    const data = notification?.request?.content?.data ?? {};
    // One the app showed itself (showPhoneNotification) is not a push: its sound already played
    if (data.local) return;
    lastPushAt = Date.now();
    onPush?.(data);
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

/**
 * Shows one of the account's notifications as the phone's own banner (heads-up popup, also kept in the tray) while
 * the app is open: on the doorbell channel for the partner's arrival, `alerts` for HIGH, else `default` (all
 * importance HIGH or MAX, so they pop up; the foreground handler keeps them silent, LiveWatcher plays the sound).
 * Returns false when it cannot (Expo Go, notifications not allowed): the app shows its own popup instead.
 */
export async function showPhoneNotification(notification) {
  const N = notifications();
  if (!N) return false;
  try {
    const { status } = await N.getPermissionsAsync();
    if (status !== 'granted') return false;
    const channelId =
      notification.notificationType === 'PARTNER_ARRIVED' ? 'doorbell' : notification.priority === 'HIGH' ? 'alerts' : 'default';
    await N.scheduleNotificationAsync({
      content: {
        title: notification.title,
        body: notification.message,
        data: {
          local: true,
          notificationId: String(notification.notificationId),
          notificationType: notification.notificationType,
          referenceType: notification.referenceType ?? '',
          referenceId: notification.referenceId == null ? '' : String(notification.referenceId)
        }
      },
      trigger: { channelId }
    });
    return true;
  } catch {
    return false;
  }
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
