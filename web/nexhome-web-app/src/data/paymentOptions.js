/** The lookups payment screens show names of (Module 7). */
/** How a gateway payment was made (V66: Cashfree's payment group), as shown; null for recorded (MANUAL) payments. */
const PAYMENT_MODES = {
  upi: 'UPI',
  credit_card: 'Credit Card',
  debit_card: 'Debit Card',
  prepaid_card: 'Prepaid Card',
  net_banking: 'Net Banking',
  wallet: 'Wallet',
  pay_later: 'Pay Later',
  cardless_emi: 'Cardless EMI',
  credit_card_emi: 'Card EMI',
  debit_card_emi: 'Card EMI',
  bank_transfer: 'Bank Transfer'
};
export const paymentModeLabel = (payment) =>
  payment?.paymentMode ? PAYMENT_MODES[payment.paymentMode] ?? payment.paymentMode.replace(/_/g, ' ') : payment?.provider === 'MANUAL' ? 'Recorded' : '—';

export const PAYMENT_LOOKUPS = [
  'PAYMENT_ORDER_STATUS',
  'PAYMENT_STATUS',
  'REFUND_STATUS',
  'PAYOUT_STATUS',
  'TRANSFER_STATUS',
  'CASH_COLLECTION_STATUS',
  'WEBHOOK_PROCESSING_STATUS',
  'COMMISSION_TYPE',
  'PAYMENT_METHOD'
];

const GREEN = 'bg-[#E9F6EF] text-[var(--green-2)]';
const BLUE = 'bg-[#EDF6FD] text-[var(--blue)]';
const AMBER = 'bg-[#FFF7DE] text-[#8A6508]';
const RED = 'bg-[#FDECEC] text-[#C53030]';
const NAVY = 'bg-[#E8EEF6] text-[var(--navy)]';
const GREY = 'bg-[var(--light)] text-[var(--muted)]';

/** Badge colours of every Module 7 status code (the codes do not clash in meaning across types). */
const TONES = {
  CREATED: AMBER,
  PENDING: AMBER,
  AUTHORIZED: BLUE,
  PAID: GREEN,
  CAPTURED: GREEN,
  SUCCEEDED: GREEN,
  SETTLED: GREEN,
  COLLECTED: GREEN,
  PROCESSED: GREEN,
  REQUESTED: AMBER,
  PROCESSING: BLUE,
  DUE: AMBER,
  ON_HOLD: NAVY,
  DISPUTED: RED,
  FAILED: RED,
  REVERSED: RED,
  EXPIRED: GREY,
  CANCELLED: GREY,
  IGNORED: GREY,
  WAIVED: GREY,
  PARTIALLY_REFUNDED: NAVY,
  REFUNDED: NAVY
};

export const moneyStatusClass = (status) => TONES[status] ?? GREY;
