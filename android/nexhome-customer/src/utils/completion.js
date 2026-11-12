import { router } from 'expo-router';

/**
 * The professional tapped Finish work on an online job (V70): the customer confirms the work is done
 * (`confirm-completion/[id]`). The screen opens by itself (from Home and the booking page) once per request.
 */
const prompted = new Set();

/** The booking waits for the customer's confirmation. */
export function needsCompletionConfirmation(booking) {
  return (
    Boolean(booking?.completionRequestedAt) &&
    ['IN_PROGRESS', 'PAUSED'].includes(booking.bookingStatus) &&
    !booking.actualEndAt
  );
}

/** Opens the confirmation screen for this request, unless it was already opened for it. */
export function promptCompletion(booking) {
  if (!needsCompletionConfirmation(booking)) return false;
  const key = `${booking.bookingId}:${booking.completionRequestedAt}`;
  if (prompted.has(key)) return false;
  prompted.add(key);
  router.push({ pathname: '/confirm-completion/[id]', params: { id: String(booking.bookingId) } });
  return true;
}

/**
 * What the final amount is made of, from the booking's pricing snapshot after the work was priced for the time
 * worked: the time worked, the package's time, the extra time and the extra amount over the booked price.
 */
export function workBreakdown(booking, items, workedMinutes) {
  const pricing = booking?.pricing ?? {};
  const worked = pricing.workedMinutes ?? workedMinutes ?? null;
  const packageMinutes = (items ?? []).find((item) => item.variant?.durationMinutes)?.variant?.durationMinutes ?? null;
  const booked = pricing.bookedTotal != null ? Number(pricing.bookedTotal) : null;
  const charged = pricing.total != null ? Number(pricing.total) : null;
  return {
    worked,
    packageMinutes,
    extraMinutes: worked != null && packageMinutes ? Math.max(0, worked - packageMinutes) : null,
    bookedAmount: booked,
    extraAmount: booked != null && charged != null ? Math.max(0, charged - booked) : 0
  };
}
