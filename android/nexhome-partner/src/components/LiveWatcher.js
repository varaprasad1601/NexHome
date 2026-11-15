import { router, usePathname } from 'expo-router';
import { useEffect, useRef } from 'react';
import { AppState } from 'react-native';

import { useAuth } from '../context/AuthProvider';
import { chime } from '../utils/chime';
import { bump, setUnread } from '../utils/live';
import { lastPushTime, registerPush, setUpPush } from '../utils/push';

const APP = 'PARTNER';
/** How often the notification count is looked at while the app is open (one small request). */
const NOTIFICATIONS_MS = 5000;
/** Moving to another screen looks again, at most this often. */
const SCREEN_LOOK_MS = 2000;

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
  const lookNow = useRef(null);

  useEffect(
    () =>
      setUpPush({
        onPush: () => {
          chime();
          bump('notifications', 'bookings');
          lookNow.current?.();
        },
        onOpen: (data) => {
          if (data?.referenceType === 'BOOKING' && data.referenceId) {
            router.push({ pathname: '/job/[id]', params: { id: String(data.referenceId) } });
          } else {
            router.push('/notifications');
          }
        }
      }),
    []
  );

  useEffect(() => {
    if (!signedIn || !partnerProfileId) {
      lastUnread.current = null;
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
          if (lastUnread.current !== null && unread > lastUnread.current) {
            // A push that came in the last minute already chimed
            if (Date.now() - lastPushTime() > 60000) chime();
            bump('notifications', 'bookings');
          }
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
