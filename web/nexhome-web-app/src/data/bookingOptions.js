/** The lookups booking screens show names of. */
export const BOOKING_LOOKUPS = [
  'BOOKING_STATUS',
  'BOOKING_PAYMENT_STATUS',
  'PAYMENT_METHOD',
  'BOOKING_TYPE',
  'ASSIGNMENT_STATUS',
  'BOOKING_TIMER_EVENT',
  'PRICING_TYPE',
  'ADDRESS_TYPE'
];

const GREEN = 'bg-[#E9F6EF] text-[var(--green-2)]';
const BLUE = 'bg-[#EDF6FD] text-[var(--blue)]';
const AMBER = 'bg-[#FFF7DE] text-[#8A6508]';
const RED = 'bg-[#FDECEC] text-[#C53030]';
const NAVY = 'bg-[#E8EEF6] text-[var(--navy)]';
const GREY = 'bg-[var(--light)] text-[var(--muted)]';

const BOOKING_STATUS_TONES = {
  PENDING: AMBER,
  CONFIRMED: BLUE,
  ASSIGNED: NAVY,
  PARTNER_EN_ROUTE: NAVY,
  IN_PROGRESS: GREEN,
  PAUSED: AMBER,
  COMPLETED: GREEN,
  CANCELLED: RED
};
const PAYMENT_STATUS_TONES = {
  PENDING: AMBER,
  AUTHORIZED: BLUE,
  PAID: GREEN,
  PARTIALLY_REFUNDED: NAVY,
  REFUNDED: NAVY,
  FAILED: RED
};
const ASSIGNMENT_STATUS_TONES = {
  OFFERED: AMBER,
  ASSIGNED: BLUE,
  ACCEPTED: GREEN,
  STARTED: NAVY,
  COMPLETED: GREEN,
  REJECTED: RED,
  CANCELLED: GREY,
  EXPIRED: GREY
};

export const bookingStatusClass = (status) => BOOKING_STATUS_TONES[status] ?? GREY;
export const paymentStatusClass = (status) => PAYMENT_STATUS_TONES[status] ?? GREY;
export const assignmentStatusClass = (status) => ASSIGNMENT_STATUS_TONES[status] ?? GREY;

/** "₹1,499" (no paise when whole). */
export function formatAmount(value) {
  if (value === null || value === undefined) return '—';
  const number = Number(value);
  return `₹${number.toLocaleString('en-IN', { minimumFractionDigits: Number.isInteger(number) ? 0 : 2, maximumFractionDigits: 2 })}`;
}

/**
 * A booking's service without its option: bookings store "Service - Option" (the option is its pricing snapshot's
 * `variantName`, or the item's variant snapshot's name).
 */
export function serviceNameOf(booking, optionName = booking?.pricing?.variantName) {
  const name = booking?.serviceName ?? '';
  return optionName && name.endsWith(` - ${optionName}`) ? name.slice(0, -(optionName.length + 3)) : name;
}

/** A booking can be cancelled until the work starts (the backend's CANCELLABLE). */
export const CANCELLABLE = ['PENDING', 'CONFIRMED', 'ASSIGNED', 'PARTNER_EN_ROUTE'];

/** The service timer's next steps, by booking status (the backend's TIMER_FROM). */
export const TIMER_ACTIONS = {
  ASSIGNED: ['START'],
  PARTNER_EN_ROUTE: ['START'],
  IN_PROGRESS: ['PAUSE', 'STOP'],
  PAUSED: ['RESUME', 'STOP']
};
