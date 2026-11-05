const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** ₹1,234 or ₹1,234.50 (Indian grouping); a dash without a value. */
export function money(value) {
  if (value === null || value === undefined || value === '') return '—';
  const number = Number(value);
  if (Number.isNaN(number)) return '—';
  const whole = Number.isInteger(number);
  return `₹${number.toLocaleString('en-IN', { minimumFractionDigits: whole ? 0 : 2, maximumFractionDigits: 2 })}`;
}

/**
 * The parts of a backend date, which arrives as "Thu, 06 Nov 2026 10:30:00 IST" (Indian time), or null:
 * { weekday, day, month, year, hours, minutes, date } with `date` the moment as a Date.
 */
export function parseApiDate(value) {
  const match = /(\w{3}), (\d{2}) (\w{3}) (\d{4}) (\d{2}):(\d{2})/.exec(value ?? '');
  if (!match) return null;
  const [, weekday, day, month, year, hours, minutes] = match;
  const monthIndex = MONTHS.indexOf(month);
  if (monthIndex < 0) return null;
  const date = new Date(`${year}-${String(monthIndex + 1).padStart(2, '0')}-${day}T${hours}:${minutes}:00+05:30`);
  return { weekday, day: Number(day), month, year: Number(year), hours: Number(hours), minutes: Number(minutes), date };
}

/** 10:30 AM */
export function timeLabel(hours, minutes) {
  const suffix = hours >= 12 ? 'PM' : 'AM';
  const hour = hours % 12 === 0 ? 12 : hours % 12;
  return `${hour}:${String(minutes).padStart(2, '0')} ${suffix}`;
}

/** "Thu, 6 Nov · 10:30 AM" from a backend date; "" without one. */
export function whenLabel(value) {
  const parts = parseApiDate(value);
  if (!parts) return '';
  return `${parts.weekday}, ${parts.day} ${parts.month} · ${timeLabel(parts.hours, parts.minutes)}`;
}

/** "6 Nov 2026" from a backend date. */
export function dayLabel(value) {
  const parts = parseApiDate(value);
  return parts ? `${parts.day} ${parts.month} ${parts.year}` : '';
}

/** "About 3 hours" / "About 45 min" */
export function durationLabel(minutes) {
  if (!minutes) return '';
  if (minutes < 60) return `About ${minutes} min`;
  const hours = Math.round((minutes / 60) * 10) / 10;
  return `About ${hours} ${hours === 1 ? 'hour' : 'hours'}`;
}

/** The service's own name: bookings store "Service - Option". */
export function serviceNameOf(name) {
  return (name ?? '').split(' - ')[0];
}

/** Today in Indian time as yyyy-MM-dd plus the next days, for the date chips. */
export function nextDays(count) {
  const now = new Date(Date.now() + 5.5 * 3600 * 1000);
  const days = [];
  for (let i = 0; i < count; i += 1) {
    const d = new Date(now.getTime() + i * 86400000);
    days.push({
      iso: d.toISOString().slice(0, 10),
      dow: i === 0 ? 'Today' : ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][d.getUTCDay()],
      day: d.getUTCDate()
    });
  }
  return days;
}

/** An Indian date (yyyy-MM-dd) and time (HH:mm) as an ISO instant. */
export function toInstant(date, time) {
  return new Date(`${date}T${time}:00+05:30`).toISOString();
}

/** "AB" from a name. */
export function initials(name) {
  return (name ?? '')
    .split(' ')
    .filter(Boolean)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}

/** Today in Indian time as yyyy-MM-dd. */
export function todayInIndia() {
  return new Date(Date.now() + 5.5 * 3600 * 1000).toISOString().slice(0, 10);
}

/** The Indian day (yyyy-MM-dd) of a backend date, or "". */
export function isoDayOf(value) {
  const parts = parseApiDate(value);
  if (!parts) return '';
  return `${parts.year}-${String(MONTHS.indexOf(parts.month) + 1).padStart(2, '0')}-${String(parts.day).padStart(2, '0')}`;
}

/** "2 h 05 min" / "45 min" from minutes. */
export function minutesLabel(minutes) {
  const total = Math.max(0, Math.round(Number(minutes) || 0));
  if (total < 60) return `${total} min`;
  return `${Math.floor(total / 60)} h ${String(total % 60).padStart(2, '0')} min`;
}

/** "6 Nov 2026" from a yyyy-MM-dd date (as the backend sends LocalDate values). */
export function localDateLabel(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? '');
  if (!match) return '';
  return `${Number(match[3])} ${MONTHS[Number(match[2]) - 1]} ${match[1]}`;
}

/** A yyyy-MM-dd text that is a real date. */
export function isIsoDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value ?? '')) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}
