import { colors } from '../theme';

const BLUE = { fg: colors.blue, bg: colors.blueTint };
const GREEN = { fg: colors.greenDark, bg: colors.greenSoft };
const AMBER = { fg: colors.amber, bg: colors.amberSoft };
const RED = { fg: colors.red, bg: colors.redSoft };
const GREY = { fg: colors.muted, bg: colors.ground };

/** How a job's (booking's) status reads to the partner, its tag colours, and its step on the 4-step progress bar. */
const BOOKING = {
  PENDING: { label: 'Pending', ...AMBER, step: 0, note: 'Waiting for NexHome to confirm.' },
  CONFIRMED: { label: 'Confirmed', ...BLUE, step: 0, note: 'Confirmed, no partner yet.' },
  ASSIGNED: { label: 'Assigned to you', ...BLUE, step: 1, note: 'Accept the job to let the customer know.' },
  PARTNER_EN_ROUTE: { label: 'Accepted', ...GREEN, step: 2, note: 'You accepted. Start work when you reach the customer.' },
  IN_PROGRESS: { label: 'Working', ...GREEN, step: 3, note: 'The work is under way.' },
  PAUSED: { label: 'Paused', ...AMBER, step: 3, note: 'The work is paused.' },
  COMPLETED: { label: 'Completed', ...GREEN, step: 4, note: 'Done. Well worked!' },
  CANCELLED: { label: 'Cancelled', ...RED, step: 0, note: 'This job was cancelled.' }
};

export function bookingStatus(code) {
  return BOOKING[code] ?? { label: code ?? '', ...GREY, step: 0, note: '' };
}

/** Jobs still to do (the Upcoming tab); the rest are Completed / past. */
export const UPCOMING = ['PENDING', 'CONFIRMED', 'ASSIGNED', 'PARTNER_EN_ROUTE', 'IN_PROGRESS', 'PAUSED'];

/** Whether a job's payment is done (paid, or refunded after paying). */
export function isPaid(job) {
  return ['PAID', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(job.paymentStatus);
}

/** A cash job whose work is finished: it stays IN_PROGRESS (with its end time) until the cash is collected. */
export function isWorkFinished(job) {
  return job.bookingStatus === 'IN_PROGRESS' && Boolean(job.actualEndAt);
}

/** A finished job whose payment has not come in yet (completed, or a cash job waiting for its cash). */
export function isPaymentPending(job) {
  return (job.bookingStatus === 'COMPLETED' || isWorkFinished(job)) && !isPaid(job);
}

/** The tag a job shows: Payment pending (amber) while a finished job is unpaid, else its status. */
export function jobTag(job) {
  return isPaymentPending(job) ? { label: 'Payment pending', ...AMBER } : bookingStatus(job.bookingStatus);
}

/** Still open: to do, or finished but not paid yet (it stays in Upcoming and on Home until it is). */
export function isOpen(job) {
  return UPCOMING.includes(job.bookingStatus) || isPaymentPending(job);
}

/** The partner onboarding statuses (PARTNER_ONBOARDING_STATUS, V18 and V52). */
const ONBOARDING = {
  NOT_STARTED: { label: 'Not started', ...GREY },
  DRAFT: { label: 'Profile draft', ...AMBER },
  UNDER_REVIEW: { label: 'Under review', ...BLUE },
  CHANGES_REQUESTED: { label: 'Changes requested', ...AMBER },
  APPROVED: { label: 'Approved', ...GREEN },
  UPDATE_REQUESTED: { label: 'Update under review', ...BLUE },
  REJECTED: { label: 'Rejected', ...RED }
};

export function onboardingStatus(code) {
  return ONBOARDING[code] ?? { label: code ?? '', ...GREY };
}

/** Statuses in which the partner edits their own profile and submits it (the backend's EDITABLE_BY_PARTNER). */
export const EDITABLE = ['NOT_STARTED', 'DRAFT', 'CHANGES_REQUESTED'];

/** Documents and bank accounts (VERIFICATION_STATUS). */
const VERIFICATION = {
  PENDING: { label: 'Pending', ...AMBER },
  IN_REVIEW: { label: 'In review', ...BLUE },
  VERIFIED: { label: 'Verified', ...GREEN },
  REJECTED: { label: 'Rejected', ...RED },
  EXPIRED: { label: 'Expired', ...RED }
};

export function verificationStatus(code) {
  return VERIFICATION[code] ?? { label: code ?? '', ...GREY };
}

const PAYOUT = {
  PENDING: { label: 'Pending', ...AMBER },
  ON_HOLD: { label: 'On hold', ...AMBER },
  PROCESSING: { label: 'Processing', ...BLUE },
  PAID: { label: 'Paid', ...GREEN },
  FAILED: { label: 'Failed', ...RED },
  CANCELLED: { label: 'Cancelled', ...GREY }
};

export function payoutStatus(code) {
  return PAYOUT[code] ?? { label: code ?? '', ...GREY };
}

/** A settlement (earnings record): a payout's status or the cash collection's. */
const CASH = {
  DUE: { label: 'Cash due', ...AMBER },
  COLLECTED: { label: 'Cash collected', ...GREEN },
  DISPUTED: { label: 'Disputed', ...RED },
  WAIVED: { label: 'Waived', ...GREY }
};

export function settlementStatus(settlement, code) {
  if (settlement === 'CASH') return CASH[code] ?? { label: code ?? 'Cash', ...GREY };
  // An online job without a payout yet: the customer has not paid
  if (!code) return { label: 'Payment pending', ...AMBER };
  return payoutStatus(code);
}

const TICKET = {
  OPEN: { label: 'Open', ...BLUE },
  ASSIGNED: { label: 'Assigned', ...BLUE },
  IN_PROGRESS: { label: 'In progress', ...AMBER },
  RESOLVED: { label: 'Resolved', ...GREEN },
  CLOSED: { label: 'Closed', ...GREY }
};

export function ticketStatus(code) {
  return TICKET[code] ?? { label: code ?? '', ...GREY };
}

const TIME_OFF = {
  UPCOMING: { label: 'Upcoming', ...BLUE },
  ONGOING: { label: 'On leave now', ...AMBER },
  PAST: { label: 'Past', ...GREY },
  CANCELLED: { label: 'Cancelled', ...RED }
};

export function timeOffStatus(code) {
  return TIME_OFF[code];
}
