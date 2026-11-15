import Constants, { ExecutionEnvironment } from 'expo-constants';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

/**
 * Push notifications (FCM, backend V53): the phone's token is registered after sign-in
 * (POST /api/notifications/devices/register) and unregistered at log-out, so a notification makes a sound and shows
 * in the phone's tray while the app is in the background or closed. The backend sends HIGH ones on the `nexhome_alerts`
 * channel, the partner's arrival (PARTNER_ARRIVED) on `nexhome_doorbell` and the rest on `nexhome_general`
 * (`CHANNELS`); the doorbell plays `assets/sounds/doorbell.wav`, the others `assets/sounds/notify.wav`. The
 * notification permission is asked on the first launch (`askOnFirstLaunch`) and again at sign-in if not allowed.
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

/** The notification channels; the backend's pushes name the same ids (NexHomeFcmPushSender). */
export const CHANNELS = { general: 'nexhome_general', alerts: 'nexhome_alerts', doorbell: 'nexhome_doorbell' };
/** Channel ids of older builds (some were made at normal importance, which never pops up): removed. */
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

/** Whether this phone is registered for push: notifications reach it in the background, in the tray. */
export function pushReady() {
  return registered;
}

/** The ids of the account's notifications now in the phone's tray (shown by push or by the app): not shown again. */
export async function presentedIds() {
  const N = notifications();
  if (!N) return [];
  try {
    const list = await N.getPresentedNotificationsAsync();
    return list.map((n) => n?.request?.content?.data?.notificationId).filter((id) => id != null && id !== '');
  } catch {
    return [];
  }
}
/** The notifications already shown (by id): one seen both by LiveWatcher's look and by its push shows once. */
const shown = new Set();

/** Marks a notification shown (its popup and sound are done). */
export function markShown(notificationId) {
  if (notificationId != null && notificationId !== '') shown.add(String(notificationId));
}

/** Whether a notification was already shown. */
export function wasShown(notificationId) {
  return notificationId != null && shown.has(String(notificationId));
}

/** When the last push came in while the app was open (its sound already played). */
export function lastPushTime() {
  return lastPushAt;
}

/**
 * Channels, the foreground behaviour and the listeners; returns a cleanup. `onPush(data)` runs when a push arrives
 * while the app is open (not one already shown by LiveWatcher), `onOpen(data)` when the person taps one (also the tap
 * that started the app). `hold(notification)` (optional) keeps a push back for later instead of showing it now
 * (the customer's payment notices while a payment screen is open); it returns true when it took it.
 */
export function setUpPush({ onPush, onOpen, hold }) {
  const N = notifications();
  if (!N) return () => {};
  // While the app is open the banner pops up with its channel's sound (the same as in the background); LiveWatcher no
  // longer plays its own chime for it
  N.setNotificationHandler({
    handleNotification: async (notification) => {
      try {
        return decide(notification);
      } catch {
        // Never lose a notification to a mistake here: show it
        return { shouldShowBanner: true, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false };
      }
    }
  });

  /** How a notification shows while the app is open: a banner, unless already shown or held for later. */
  function decide(notification) {
    const content = notification?.request?.content ?? {};
    const data = content.data ?? {};
    // Ours (local) always shows; a push already shown by LiveWatcher, or held for later, does not pop up again
    let quiet = !data.local && wasShown(data.notificationId);
    if (!data.local && !quiet && hold) {
      const taken = hold({
        notificationId: data.notificationId,
        notificationType: data.notificationType,
        priority: data.priority,
        referenceType: data.referenceType,
        referenceId: data.referenceId,
        title: content.title,
        message: content.body
      });
      if (taken) {
        markShown(data.notificationId);
        quiet = true;
      }
    }
    // shouldPlaySound must be true for a banner: with it false expo marks the notification silent, and Android never
    // pops up (heads-up) a silent one; the channel plays its sound (notify.wav, the doorbell for PARTNER_ARRIVED)
    return { shouldShowBanner: !quiet, shouldShowList: !quiet, shouldPlaySound: !quiet, shouldSetBadge: false };
  }
  // The channels (Android keeps a channel's importance once made, so these are new ids made HIGH / MAX from the start:
  // banners pop up on screen); the ids of older builds are removed. Then, on the first launch, the permission is asked.
  const channel = { sound: 'notify.wav', vibrationPattern: [0, 250, 150, 250], lightColor: '#0b438b', showBadge: true };
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
    const data = notification?.request?.content?.data ?? {};
    // One the app showed itself (showPhoneNotification) is not a push: its sound already played
    if (data.local) return;
    lastPushAt = Date.now();
    // Already shown (LiveWatcher's look came first) or held: no second sound
    if (wasShown(data.notificationId)) return;
    markShown(data.notificationId);
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
 * the app is open: on the doorbell channel for the partner's arrival, the alerts channel for HIGH, else the general
 * one (importance HIGH or MAX, so they pop up, with the channel's sound).
 * Returns false when it cannot (Expo Go, notifications not allowed): the app shows its own popup instead.
 */
export async function showPhoneNotification(notification) {
  const N = notifications();
  if (!N) return false;
  try {
    const { status } = await N.getPermissionsAsync();
    if (status !== 'granted') return false;
    const channelId =
      notification.notificationType === 'PARTNER_ARRIVED'
        ? CHANNELS.doorbell
        : notification.priority === 'HIGH'
          ? CHANNELS.alerts
          : CHANNELS.general;
    await N.scheduleNotificationAsync({
      content: {
        title: notification.title,
        body: notification.message,
        // Heads-up on older Android too (newer ones go by the channel's importance)
        priority: N.AndroidNotificationPriority?.MAX ?? 'max',
        // A sound and a vibration, or expo marks it silent and Android never pops it up (the channel's sound plays)
        sound: channelId === CHANNELS.doorbell ? 'doorbell.wav' : 'notify.wav',
        vibrate: [0, 250, 150, 250],
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
    registered = true;
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
  registered = false;
  await authRequest('/api/notifications/devices/unregister', { method: 'PUT', body: { deviceToken } }).catch(() => {});
}
