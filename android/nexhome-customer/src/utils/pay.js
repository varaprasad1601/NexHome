import { router } from 'expo-router';

/** Where the payment page goes when it is done or closed; the pay screen catches it, so it never really opens. */
export const PAYMENT_RETURN = 'nexhome://payment-result';

/** Attempt outcomes after which the payment page has nothing more to do (Cashfree's payment_status). */
export const ATTEMPT_DONE = ['SUCCESS', 'FAILED', 'USER_DROPPED', 'CANCELLED', 'VOID', 'PENDING'];

/**
 * Where the payment flow ends: `back` returns to the screen that started it (the booking page), any other value
 * is a route that replaces the flow (after checkout: the new booking, or the Bookings tab).
 */
export function finishPayment(next) {
  if (!next || next === 'back') {
    if (router.canGoBack()) router.back();
    else router.replace('/(tabs)/bookings');
  } else {
    router.replace(next);
  }
}

/**
 * Pays an online booking (and the other unpaid online bookings of its checkout) through the payment gateway:
 * creates or reuses the payment order (POST /api/payments/orders/create) and opens the secure payment page inside
 * the app (the `pay` screen: a WebView of GET /api/payments/checkout, Cashfree's checkout). The flow then goes on by
 * itself: `pay` -> `payment-status` (Processing, then Payment successful / failed / pending) -> `rate/[id]` (a
 * completed booking not yet rated) -> `next` (see finishPayment).
 *
 * The page is shown in the app rather than the phone's browser: the browser may not reach a backend on the local
 * network, and could not come back to the app by itself.
 *
 * `replace`: the payment screen replaces the current one (after checkout, and Try again). Resolves with PAID when the
 * order was already paid (nothing opens), else OPENED. Throws when online payment is not set up (no checkout) or the
 * gateway cannot be reached.
 */
export async function payOnline(authRequest, bookingId, { next = 'back', replace = false } = {}) {
  const order = await authRequest('/api/payments/orders/create', { method: 'POST', body: { bookingId } });
  if (order.paymentStatus === 'PAID') return 'PAID';
  if (order.paymentStatus !== 'CREATED' && order.paymentStatus !== 'PENDING') {
    throw new Error('This payment can no longer be made. Please try again.');
  }
  if (!order.checkoutToken) {
    throw new Error('Online payment is not available yet. Our team will contact you to collect it.');
  }
  const target = {
    pathname: '/pay',
    params: { session: order.checkoutToken, orderId: String(order.paymentOrderId), bookingId: String(bookingId), next }
  };
  if (replace) router.replace(target);
  else router.push(target);
  return 'OPENED';
}
