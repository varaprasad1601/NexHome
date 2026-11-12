const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** A payout's day ("2026-11-11") as "11 Nov 2026". */
export function paidOnLabel(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? '');
  if (!match) return value ?? '—';
  return `${Number(match[3])} ${MONTHS[Number(match[2]) - 1]} ${match[1]}`;
}

/** The PAYOUT_MODE name (useLookupLabels' `label`), the code until the lookup has loaded. */
export function payoutModeLabel(label, code) {
  return code ? label('PAYOUT_MODE', code) || code : '—';
}
