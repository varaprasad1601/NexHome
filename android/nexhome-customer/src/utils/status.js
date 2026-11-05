import { colors } from '../theme';
import { parseApiDate } from './format';

/** How a booking's status reads to the customer, its tag colours, and its step on the 4-step progress bar. */
const BOOKING = {
  PENDING: { label: 'Pending', fg: colors.amber, bg: colors.amberSoft, step: 1, note: 'We are confirming your booking.' },
  CONFIRMED: { label: 'Confirmed', fg: colors.blue, bg: colors.blueTint, step: 1, note: 'Confirmed. We are finding a professional for you.' },
  ASSIGNED: { label: 'Partner assigned', fg: colors.blue, bg: colors.blueTint, step: 2, note: 'A professional has been assigned.' },
  PARTNER_EN_ROUTE: { label: 'Partner accepted', fg: colors.greenDark, bg: colors.greenSoft, step: 3, note: 'Your professional accepted the job.' },
  IN_PROGRESS: { label: 'In progress', fg: colors.greenDark, bg: colors.greenSoft, step: 3, note: 'The work is under way.' },
  PAUSED: { label: 'Paused', fg: colors.amber, bg: colors.amberSoft, step: 3, note: 'The work is paused for now.' },
  COMPLETED: { label: 'Completed', fg: colors.greenDark, bg: colors.greenSoft, step: 4, note: 'Done. Thank you for choosing NexHome.' },
  CANCELLED: { label: 'Cancelled', fg: colors.red, bg: colors.redSoft, step: 0, note: 'This booking was cancelled.' }
};

export function bookingStatus(code) {
  return BOOKING[code] ?? { label: code ?? '', fg: colors.muted, bg: colors.ground, step: 0, note: '' };
}

/** Bookings still to come (the Upcoming tab); the rest are Past. */
export const UPCOMING = ['PENDING', 'CONFIRMED', 'ASSIGNED', 'PARTNER_EN_ROUTE', 'IN_PROGRESS', 'PAUSED'];

/** How urgent an upcoming booking is: work under way first, then a partner on it, then confirmed, then pending. */
const PRIORITY = { IN_PROGRESS: 0, PAUSED: 0, ASSIGNED: 1, PARTNER_EN_ROUTE: 1, CONFIRMED: 2, PENDING: 3 };

/**
 * The order of upcoming bookings: by status (in progress, partner assigned or accepted, confirmed, pending), then
 * by time (as soon as possible first, then the earliest scheduled start), then by booking id, lowest first.
 */
export function compareUpcoming(a, b) {
  const rank = (x) => PRIORITY[x.bookingStatus] ?? 9;
  if (rank(a) !== rank(b)) return rank(a) - rank(b);
  const at = (x) => (x.scheduledStartAt ? parseApiDate(x.scheduledStartAt)?.date.getTime() ?? Infinity : -Infinity);
  if (at(a) !== at(b)) return at(a) - at(b);
  return Number(a.bookingId) - Number(b.bookingId);
}

/** Newest first by a backend date field (actualEndAt for completed, cancelledAt for cancelled); unknown dates last. */
export function newestFirstBy(field) {
  const at = (x) => parseApiDate(x[field])?.date.getTime() ?? -Infinity;
  return (a, b) => at(b) - at(a) || Number(b.bookingId) - Number(a.bookingId);
}

/** The customer may cancel until the work starts (the backend's CANCELLABLE). */
export const CANCELLABLE = ['PENDING', 'CONFIRMED', 'ASSIGNED', 'PARTNER_EN_ROUTE'];

const PAYMENT = {
  PENDING: { label: 'Payment pending', fg: colors.amber, bg: colors.amberSoft },
  PAID: { label: 'Paid', fg: colors.greenDark, bg: colors.greenSoft },
  FAILED: { label: 'Payment failed', fg: colors.red, bg: colors.redSoft },
  PARTIALLY_REFUNDED: { label: 'Partly refunded', fg: colors.blue, bg: colors.blueTint },
  REFUNDED: { label: 'Refunded', fg: colors.blue, bg: colors.blueTint }
};

export function paymentStatus(code) {
  return PAYMENT[code] ?? null;
}

const TICKET = {
  OPEN: { label: 'Open', fg: colors.blue, bg: colors.blueTint },
  ASSIGNED: { label: 'Assigned', fg: colors.blue, bg: colors.blueTint },
  IN_PROGRESS: { label: 'In progress', fg: colors.amber, bg: colors.amberSoft },
  RESOLVED: { label: 'Resolved', fg: colors.greenDark, bg: colors.greenSoft },
  CLOSED: { label: 'Closed', fg: colors.muted, bg: colors.ground }
};

export function ticketStatus(code) {
  return TICKET[code] ?? { label: code ?? '', fg: colors.muted, bg: colors.ground };
}
