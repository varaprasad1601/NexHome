/**
 * Payment notifications wait while the customer pays: the checkout (`pay`) and the result screen (`payment-status`)
 * hold them, so "Payment received / failed / pending" does not pop up over the checkout or before the result screen
 * says it. The result screen lets them go once it shows the outcome; they then pop up (sound and banner) a moment
 * later. `components/LiveWatcher` and the push handler (`utils/push`) hold PAYMENT notifications through `isHeld`.
 */
let holders = 0;
const held = [];
let flusher = null;
let timer = null;

/** Starts holding; returns the function that stops this hold (safe to call twice). */
export function holdPaymentNotices() {
  holders += 1;
  clearTimeout(timer);
  let released = false;
  return (delayMs = 900) => {
    if (released) return;
    released = true;
    holders = Math.max(0, holders - 1);
    if (holders > 0) return;
    clearTimeout(timer);
    timer = setTimeout(() => {
      if (holders > 0) return;
      const items = held.splice(0);
      if (items.length && flusher) flusher(items);
    }, delayMs);
  };
}

/** Whether this notification waits now (a PAYMENT one while a payment screen is open). */
export function isHeld(notification) {
  return holders > 0 && notification?.notificationType === 'PAYMENT';
}

/** Keeps a held notification for later (once per id). */
export function holdNotice(notification) {
  if (!held.some((n) => String(n.notificationId) === String(notification.notificationId))) held.push(notification);
}

/** Who shows the held notifications once released (LiveWatcher). */
export function onRelease(fn) {
  flusher = fn;
  return () => {
    if (flusher === fn) flusher = null;
  };
}
