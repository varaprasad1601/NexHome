import { router, usePathname } from 'expo-router';
import { useEffect, useRef } from 'react';
import { AppState } from 'react-native';

import { useAuth } from '../context/AuthProvider';
import { chime, doorbell } from '../utils/chime';
import { bump, setUnread } from '../utils/live';
import { lastPushTime, registerPush, setUpPush, showPhoneNotification } from '../utils/push';
import { showNotice } from './NoticeBanner';

const APP = 'PARTNER';
/** How often the notification count is looked at while the app is open (one small request). */
const NOTIFICATIONS_MS = 5000;
/** Moving to another screen looks again, at most this often. */
const SCREEN_LOOK_MS = 2000;

/** Opens what a notification is about: its job, else the notifications list. */
function openTarget(data) {
  if (data?.referenceType === 'BOOKING' && data.referenceId) {
    router.push({ pathname: '/job/[id]', params: { id: String(data.referenceId) } });
  } else {
    router.push('/notifications');
  }
}

/**
 * Rendered once in _layout, nothing on screen. While the partner is signed in and the app is open, on every screen:
 * the notification count (GET /api/notifications/me/summary?app=PARTNER) every 5 s, whenever another screen opens
 * (at most every 2 s) and when the app comes back to the front; a new notification (a job offered or assigned,
 * cancelled, a payout, ...) plays the NexHome chime and reloads the focused screen's offers, jobs and notifications
 * (`bump`). No requests while the app is in the background (the timer keeps going, so a look is never lost). Push (in
 * a build): registers the phone, so notifications sound and show in the tray while the app is in the background or
 * closed; one arriving while the app is open chimes at once; tapping one opens its job.
 */
export default function LiveWatcher() {
  const { signedIn, profile, authRequest } = useAuth();
  const partnerProfileId = profile?.partnerProfileId;
  const pathname = usePathname();
  const lastUnread = useRef(null);
  const lastSeenId = useRef(null);
  const lookNow = useRef(null);

  useEffect(
    () =>
      setUpPush({
        onPush: () => {
          chime();
          bump('notifications', 'bookings');
          lookNow.current?.();
        },
        onOpen: openTarget
      }),
    []
  );

  useEffect(() => {
    if (!signedIn || !partnerProfileId) {
      lastUnread.current = null;
      lastSeenId.current = null;
      lookNow.current = null;
      setUnread(null);
      return undefined;
    }
    registerPush(authRequest, APP);
    let cancelled = false;
    let timer = null;
    let looking = false;
    let lastLook = 0;
    const active = () => AppState.currentState === 'active' || AppState.currentState == null;

    /** The newest notification's id when the watcher started (only later ones pop up). */
    async function baseline() {
      try {
        const page = await authRequest(`/api/notifications/me?size=1&app=${APP}`);
        lastSeenId.current = page?.content?.[0]?.notificationId ?? 0;
      } catch {
        lastSeenId.current = 0;
      }
    }

    /**
     * New notifications arrived: each pops up (the phone's banner, else the app's own) with one sound, the doorbell
     * when the professional reached the door, else the chime. A push of the last minute already did both.
     */
    async function present() {
      let fresh = [];
      try {
        const page = await authRequest(`/api/notifications/me?size=5&unread=true&app=${APP}`);
        fresh = (page?.content ?? []).filter((n) => n.notificationId > (lastSeenId.current ?? 0)).reverse();
      } catch {
        // Unknown which ones: the sound alone
      }
      if (fresh.length) lastSeenId.current = Math.max(lastSeenId.current ?? 0, ...fresh.map((n) => n.notificationId));
      if (Date.now() - lastPushTime() <= 60000) return;
      if (fresh.some((n) => n.notificationType === 'PARTNER_ARRIVED')) doorbell();
      else chime();
      for (const n of fresh.slice(-3)) {
        if (cancelled) return;
        const shown = await showPhoneNotification(n);
        if (!shown) {
          showNotice({
            title: n.title,
            body: n.message,
            priority: n.priority,
            icon: n.notificationType === 'PARTNER_ARRIVED' ? 'map-pin' : 'bell',
            onPress: () => openTarget(n)
          });
        }
      }
    }

    async function look() {
      // One running already schedules the next look
      if (cancelled || looking) return;
      clearTimeout(timer);
      if (active()) {
        looking = true;
        lastLook = Date.now();
        try {
          const summary = await authRequest(`/api/notifications/me/summary?app=${APP}`);
          if (cancelled) return;
          const unread = summary?.unread ?? 0;
          if (lastUnread.current === null) await baseline();
          else if (unread > lastUnread.current) {
            await present();
            bump('notifications', 'bookings');
          }
          if (cancelled) return;
          lastUnread.current = unread;
          setUnread(unread);
        } catch {
          // Next look
        } finally {
          looking = false;
        }
      }
      // Always the next look, whatever happened, so the chain never stops
      if (!cancelled) timer = setTimeout(look, NOTIFICATIONS_MS);
    }

    lookNow.current = () => {
      if (Date.now() - lastLook >= SCREEN_LOOK_MS) look();
    };
    look();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') look();
    });
    return () => {
      cancelled = true;
      lookNow.current = null;
      clearTimeout(timer);
      subscription.remove();
    };
  }, [signedIn, partnerProfileId, authRequest]);

  // Another screen opened: look again (at most every 2 s), so nothing waits for the next 5-second look
  useEffect(() => {
    lookNow.current?.();
  }, [pathname]);

  return null;
}
