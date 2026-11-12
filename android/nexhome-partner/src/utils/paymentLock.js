import { useSyncExternalStore } from 'react';

/**
 * The job whose online payment the partner must wait for (V70: the customer confirmed the work is done, it is not
 * paid yet): while set, the app shows only its Payment processing screen (`await-payment/[id]`) and Help & support
 * (`components/PaymentLock`). Cleared when it is paid or the cash is collected.
 */
let lockedId = null;
const listeners = new Set();

export function setPaymentLock(bookingId) {
  const next = bookingId == null ? null : Number(bookingId);
  if (next === lockedId) return;
  lockedId = next;
  listeners.forEach((listener) => listener());
}

function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** The locked job's id, or null. */
export function usePaymentLock() {
  return useSyncExternalStore(subscribe, () => lockedId);
}

/**
 * A job the partner must wait on: its work finished on the customer's confirmation (IN_PROGRESS with its end time),
 * online, not paid. Paying completes it; so do NexHome staff (Complete Job), which ends the wait too.
 */
export function isLockedJob(job) {
  return (
    job?.bookingStatus === 'IN_PROGRESS' &&
    Boolean(job.actualEndAt) &&
    job.paymentMethod === 'ONLINE' &&
    Boolean(job.completionConfirmedAt) &&
    !['PAID', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(job.paymentStatus)
  );
}
