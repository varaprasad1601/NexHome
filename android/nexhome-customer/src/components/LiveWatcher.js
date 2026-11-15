import { router } from 'expo-router';
import { useEffect, useRef } from 'react';
import { AppState } from 'react-native';

import { useAuth } from '../context/AuthProvider';
import { chime } from '../utils/chime';
import { promptCompletion } from '../utils/completion';
import { bump, setUnread } from '../utils/live';
import { lastPushTime, registerPush, setUpPush } from '../utils/push';

const APP = 'CUSTOMER';
/** How often the notification count is looked at while the app is open (one small request). */
const NOTIFICATIONS_MS = 30000;
/** A professional is assigned, on the way or working: they may act at any moment. */
const ACTIVE = ['ASSIGNED', 'PARTNER_EN_ROUTE', 'IN_PROGRESS', 'PAUSED'];
/** Waiting for NexHome (confirm, assign) or for the payment. */
const WAITING = ['PENDING', 'CONFIRMED'];

/** What of a booking the screens show; a change reloads them. */
const signatureOf = (bookings) =>
  bookings
    .map((b) =>
      [b.bookingId, b.bookingStatus, b.paymentStatus, b.partnerProfileId, b.actualStartAt, b.actualEndAt, b.completionRequestedAt, b.totalAmount].join(':')
    )
    .join('|');

/**
 * Rendered once in _layout, nothing on screen. While the customer is signed in and the app is open, on any screen:
 *
 * - the notification count (GET /api/notifications/me/summary?app=CUSTOMER) every 30 s: a new notification plays the
 *   NexHome chime (unless its push already sounded) and reloads the focused screen;
 * - the bookings (GET /api/bookings/me) every 15 s while a professional is assigned, on the way or working, every
 *   60 s while one waits for NexHome or a finished job for its payment, never otherwise: any change (accepted,
 *   started, paused, finished, paid) reloads Home, Bookings and the booking page (`bump('bookings')`), and the
 *   professional's Finish work opens "Is your job done?" from whatever screen is open;
 * - both again as soon as the app comes back to the front; nothing while it is in the background;
 * - push (in a build): registers the phone, so notifications sound and show in the tray while the app is in the
 *   background or closed; tapping one opens its booking.
 */
export default function LiveWatcher() {
  const { signedIn, profile, authRequest } = useAuth();
  const customerProfileId = profile?.customerProfileId;
  const lastUnread = useRef(null);
  const lastSignature = useRef(null);

  // Tapping a push opens what it is about
  useEffect(
    () =>
      setUpPush({
        onPush: () => bump('notifications', 'bookings'),
        onOpen: (data) => {
          if (data?.referenceType === 'BOOKING' && data.referenceId) {
            router.push({ pathname: '/booking/[id]', params: { id: String(data.referenceId) } });
          } else {
            router.push('/notifications');
          }
        }
      }),
    []
  );

  useEffect(() => {
    if (!signedIn || !customerProfileId) {
      lastUnread.current = null;
      lastSignature.current = null;
      setUnread(null);
      return undefined;
    }
    registerPush(authRequest, APP);
    let cancelled = false;
    let bookingTimer = null;
    let noticeTimer = null;
    let lookingBookings = false;

    async function lookBookings() {
      clearTimeout(bookingTimer);
      if (cancelled || lookingBookings || AppState.currentState !== 'active') return;
      lookingBookings = true;
      let next = null;
      try {
        const bookings = (await authRequest('/api/bookings/me?size=20'))?.content ?? [];
        if (cancelled) return;
        const signature = signatureOf(bookings);
        if (lastSignature.current !== null && signature !== lastSignature.current) bump('bookings');
        lastSignature.current = signature;
        for (const booking of bookings) {
          if (promptCompletion(booking)) break;
        }
        if (bookings.some((b) => ACTIVE.includes(b.bookingStatus) && !b.actualEndAt)) next = 15000;
        else if (bookings.some((b) => WAITING.includes(b.bookingStatus) || (b.bookingStatus === 'COMPLETED' && b.paymentStatus === 'PENDING'))) next = 60000;
      } catch {
        next = 60000;
      } finally {
        lookingBookings = false;
      }
      if (!cancelled && next) bookingTimer = setTimeout(lookBookings, next);
    }

    async function lookNotifications() {
      clearTimeout(noticeTimer);
      if (cancelled || AppState.currentState !== 'active') return;
      try {
        const summary = await authRequest(`/api/notifications/me/summary?app=${APP}`);
        if (cancelled) return;
        const unread = summary?.unread ?? 0;
        if (lastUnread.current !== null && unread > lastUnread.current) {
          // Its push already played the sound if it came in the last minute
          if (Date.now() - lastPushTime() > 60000) chime();
          bump('notifications', 'bookings');
          lookBookings();
        }
        lastUnread.current = unread;
        setUnread(unread);
      } catch {
        // Next look
      }
      if (!cancelled) noticeTimer = setTimeout(lookNotifications, NOTIFICATIONS_MS);
    }

    lookNotifications();
    lookBookings();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        lookNotifications();
        lookBookings();
      } else {
        clearTimeout(bookingTimer);
        clearTimeout(noticeTimer);
      }
    });
    return () => {
      cancelled = true;
      clearTimeout(bookingTimer);
      clearTimeout(noticeTimer);
      subscription.remove();
    };
  }, [signedIn, customerProfileId, authRequest]);

  return null;
}
