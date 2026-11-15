import { router, usePathname } from 'expo-router';
import { useEffect, useRef } from 'react';
import { AppState } from 'react-native';

import { useAuth } from '../context/AuthProvider';
import { chime, doorbell } from '../utils/chime';
import { promptCompletion } from '../utils/completion';
import { markRatingShown, promptRating, wasRatingShown } from '../utils/rating';
import { isPaid } from '../utils/status';
import { bump, setUnread } from '../utils/live';
import { lastPushTime, registerPush, setUpPush, showPhoneNotification } from '../utils/push';
import { showNotice } from './NoticeBanner';

const APP = 'CUSTOMER';
/** How often the notification count is looked at while the app is open (one small request). */
const NOTIFICATIONS_MS = 5000;
/** A professional is assigned, on the way or working: they may act at any moment. */
const ACTIVE = ['ASSIGNED', 'PARTNER_EN_ROUTE', 'IN_PROGRESS', 'PAUSED'];
/** Waiting for NexHome (confirm, assign) or for the payment. */
const WAITING = ['PENDING', 'CONFIRMED'];
/** Moving to another screen looks again, at most this often. */
const SCREEN_LOOK_MS = 2000;
/** Screens that open the rating / payment confirmation themselves, or must not be covered by it. */
const OWN_FLOW = ['/pay', '/payment-status', '/payment-result', '/rate', '/confirm-completion', '/login', '/otp'];

/** What of a booking the screens show; a change reloads them. */
const signatureOf = (bookings) =>
  bookings
    .map((b) =>
      [b.bookingId, b.bookingStatus, b.paymentStatus, b.partnerProfileId, b.partnerArrivedAt, b.actualStartAt, b.actualEndAt, b.completionRequestedAt, b.totalAmount].join(':')
    )
    .join('|');

/** Opens what a notification is about: its booking, else the notifications list. */
function openTarget(data) {
  if (data?.referenceType === 'BOOKING' && data.referenceId) {
    router.push({ pathname: '/booking/[id]', params: { id: String(data.referenceId) } });
  } else {
    router.push('/notifications');
  }
}

/**
 * A change the customer should hear about at once, even before its notification: the professional accepted (now
 * Partner accepted), or declined / was taken off (assigned or accepted, now waiting for a partner again).
 */
function soundWorthy(before, now) {
  if (!before || before.bookingStatus === now.bookingStatus) return false;
  if (now.bookingStatus === 'PARTNER_EN_ROUTE') return true;
  return now.bookingStatus === 'CONFIRMED' && ['ASSIGNED', 'PARTNER_EN_ROUTE'].includes(before.bookingStatus);
}

/**
 * Rendered once in _layout, nothing on screen. While the customer is signed in and the app is open, on every screen:
 *
 * - the notification count (GET /api/notifications/me/summary?app=CUSTOMER) every 5 s and whenever another screen
 *   opens (at most every 2 s): a new notification plays the NexHome chime and reloads the focused screen;
 * - the bookings (GET /api/bookings/me) every 15 s while a professional is assigned, on the way or working, every
 *   60 s while one waits for NexHome or a finished job for its payment, never otherwise: any change reloads Home,
 *   Bookings and the booking page (`bump('bookings')`); the professional accepting or declining also chimes; their
 *   Finish work opens "Is your job done?", and a job done and paid but not rated opens "Confirm your payment" (cash)
 *   and the rating, from whatever screen is open (not over the pay flow, which opens it itself);
 * - both again as soon as the app comes back to the front; no requests while it is in the background (the timers
 *   keep going, so a look is never lost);
 * - push (in a build): registers the phone, so notifications sound and show in the tray while the app is in the
 *   background or closed; one arriving while the app is open chimes at once; tapping one opens its booking.
 */
export default function LiveWatcher() {
  const { signedIn, profile, authRequest } = useAuth();
  const customerProfileId = profile?.customerProfileId;
  const pathname = usePathname();
  const pathRef = useRef(pathname);
  const lastUnread = useRef(null);
  const lastSeenId = useRef(null);
  const lastSignature = useRef(null);
  const lastStatuses = useRef(new Map());
  const lookNow = useRef(null);

  // A push while the app is open: the chime at once, and the screens reload; tapping one opens what it is about
  useEffect(
    () =>
      setUpPush({
        onPush: (data) => {
          // The professional at the door rings the doorbell, anything else the chime
          if (data?.notificationType === 'PARTNER_ARRIVED') doorbell();
          else chime();
          bump('notifications', 'bookings');
          lookNow.current?.();
        },
        onOpen: openTarget
      }),
    []
  );

  useEffect(() => {
    if (!signedIn || !customerProfileId) {
      lastUnread.current = null;
      lastSeenId.current = null;
      lastSignature.current = null;
      lastStatuses.current = new Map();
      lookNow.current = null;
      setUnread(null);
      return undefined;
    }
    registerPush(authRequest, APP);
    let cancelled = false;
    let bookingTimer = null;
    let noticeTimer = null;
    let lookingBookings = false;
    let lookingNotices = false;
    let lastLook = 0;
    const active = () => AppState.currentState === 'active' || AppState.currentState == null;

    async function lookBookings() {
      // One running already schedules the next look
      if (cancelled || lookingBookings) return;
      clearTimeout(bookingTimer);
      let next = 60000;
      if (active()) {
        lookingBookings = true;
        next = null;
        try {
          const bookings = (await authRequest('/api/bookings/me?size=20'))?.content ?? [];
          if (cancelled) return;
          const signature = signatureOf(bookings);
          if (lastSignature.current !== null && signature !== lastSignature.current) {
            if (bookings.some((b) => soundWorthy(lastStatuses.current.get(b.bookingId), b))) chime();
            bump('bookings');
          }
          lastSignature.current = signature;
          lastStatuses.current = new Map(bookings.map((b) => [b.bookingId, b]));
          let prompted = false;
          for (const booking of bookings) {
            if (promptCompletion(booking)) {
              prompted = true;
              break;
            }
          }
          // A job done and paid (the cash collected, or paid online) and not rated yet: "Confirm your payment" (cash)
          // and the rating open from any screen, once a session (never for one skipped), not over the pay flow
          const done = bookings.filter((b) => b.bookingStatus === 'COMPLETED' && isPaid(b) && b.partnerProfileId && !wasRatingShown(b.bookingId));
          const busy = OWN_FLOW.some((path) => pathRef.current?.startsWith(path));
          if (!prompted && done.length && !busy) {
            const reviews = await authRequest('/api/reviews/me?size=100').catch(() => null);
            if (!cancelled && reviews) {
              const reviewed = new Set((reviews.content ?? []).map((r) => r.bookingId));
              // Rated ones are never asked about again this session (and never make this request again)
              done.filter((b) => reviewed.has(b.bookingId)).forEach((b) => markRatingShown(b.bookingId));
              const unrated = done.find((b) => !reviewed.has(b.bookingId));
              if (unrated) promptRating(unrated.bookingId);
            }
          }
          if (bookings.some((b) => ACTIVE.includes(b.bookingStatus) && !b.actualEndAt)) next = 15000;
          else if (bookings.some((b) => WAITING.includes(b.bookingStatus) || (b.bookingStatus === 'COMPLETED' && b.paymentStatus === 'PENDING'))) next = 60000;
        } catch {
          next = 60000;
        } finally {
          lookingBookings = false;
        }
      }
      if (!cancelled && next) bookingTimer = setTimeout(lookBookings, next);
    }

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

    async function lookNotifications() {
      if (cancelled || lookingNotices) return;
      clearTimeout(noticeTimer);
      if (active()) {
        lookingNotices = true;
        lastLook = Date.now();
        try {
          const summary = await authRequest(`/api/notifications/me/summary?app=${APP}`);
          if (cancelled) return;
          const unread = summary?.unread ?? 0;
          if (lastUnread.current === null) await baseline();
          else if (unread > lastUnread.current) {
            await present();
            bump('notifications', 'bookings');
            lookBookings();
          }
          if (cancelled) return;
          lastUnread.current = unread;
          setUnread(unread);
        } catch {
          // Next look
        } finally {
          lookingNotices = false;
        }
      }
      // Always the next look, whatever happened, so the chain never stops
      if (!cancelled) noticeTimer = setTimeout(lookNotifications, NOTIFICATIONS_MS);
    }

    lookNow.current = () => {
      if (Date.now() - lastLook < SCREEN_LOOK_MS) return;
      lookNotifications();
      lookBookings();
    };
    lookNotifications();
    lookBookings();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        lookNotifications();
        lookBookings();
      }
    });
    return () => {
      cancelled = true;
      lookNow.current = null;
      clearTimeout(bookingTimer);
      clearTimeout(noticeTimer);
      subscription.remove();
    };
  }, [signedIn, customerProfileId, authRequest]);

  // Another screen opened: look again (at most every 2 s), so nothing waits for the next 5-second look
  useEffect(() => {
    pathRef.current = pathname;
    lookNow.current?.();
  }, [pathname]);

  return null;
}
