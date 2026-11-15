import { router } from 'expo-router';
import { useEffect, useRef } from 'react';
import { AppState } from 'react-native';

import { useAuth } from '../context/AuthProvider';
import { chime } from '../utils/chime';
import { bump, setUnread } from '../utils/live';
import { lastPushTime, registerPush, setUpPush } from '../utils/push';

const APP = 'PARTNER';
/** How often the notification count is looked at while the app is open (one small request). */
const NOTIFICATIONS_MS = 30000;

/**
 * Rendered once in _layout, nothing on screen. While the partner is signed in and the app is open, on any screen:
 * the notification count (GET /api/notifications/me/summary?app=PARTNER) every 30 s and when the app comes back to
 * the front; a new notification (a job offered or assigned, cancelled, a payout, ...) plays the NexHome chime (unless
 * its push already sounded) and reloads the focused screen's offers, jobs and notifications (`bump`). Nothing while
 * the app is in the background. Push (in a build): registers the phone, so notifications sound and show in the tray
 * while the app is in the background or closed; tapping one opens its job.
 */
export default function LiveWatcher() {
  const { signedIn, profile, authRequest } = useAuth();
  const partnerProfileId = profile?.partnerProfileId;
  const lastUnread = useRef(null);

  useEffect(
    () =>
      setUpPush({
        onPush: () => bump('notifications', 'bookings'),
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
      setUnread(null);
      return undefined;
    }
    registerPush(authRequest, APP);
    let cancelled = false;
    let timer = null;

    async function look() {
      clearTimeout(timer);
      if (cancelled || AppState.currentState !== 'active') return;
      try {
        const summary = await authRequest(`/api/notifications/me/summary?app=${APP}`);
        if (cancelled) return;
        const unread = summary?.unread ?? 0;
        if (lastUnread.current !== null && unread > lastUnread.current) {
          // Its push already played the sound if it came in the last minute
          if (Date.now() - lastPushTime() > 60000) chime();
          bump('notifications', 'bookings');
        }
        lastUnread.current = unread;
        setUnread(unread);
      } catch {
        // Next look
      }
      if (!cancelled) timer = setTimeout(look, NOTIFICATIONS_MS);
    }

    look();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') look();
      else clearTimeout(timer);
    });
    return () => {
      cancelled = true;
      clearTimeout(timer);
      subscription.remove();
    };
  }, [signedIn, partnerProfileId, authRequest]);

  return null;
}
