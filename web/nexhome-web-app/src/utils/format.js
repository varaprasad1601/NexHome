const NUMBER = new Intl.NumberFormat('en-IN');

/** 12840 → "12,840" (Indian digit grouping: 18,75,000). */
export function formatNumber(value, decimals = 0) {
  return decimals
    ? value.toLocaleString('en-IN', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
    : NUMBER.format(value);
}

/** 1875000 → "₹18,75,000". */
export function formatRupees(value) {
  return `₹${NUMBER.format(value)}`;
}

/**
 * A backend date ("Thu, 08 Oct 2026 14:29:20 IST", already in IST) as { date: "08 Oct 2026", time: "02:29 PM" };
 * null for a missing or unexpected value.
 */
export function splitApiDateTime(value) {
  const match = /(\d{2}) (\w{3}) (\d{4}) (\d{2}):(\d{2})/.exec(value ?? '');
  if (!match) return null;
  const [, day, month, year, hours, minutes] = match;
  const hour = Number(hours);
  const hour12 = String(hour % 12 || 12).padStart(2, '0');
  return { date: `${day} ${month} ${year}`, time: `${hour12}:${minutes} ${hour < 12 ? 'AM' : 'PM'}` };
}

/** A date as "01 Aug 2024". */
export function formatDay(date) {
  return date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}
