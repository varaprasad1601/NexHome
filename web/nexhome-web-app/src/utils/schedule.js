/**
 * Dates and times for availability (Module 5). The console works in Indian time: a date (yyyy-MM-dd) and a time
 * (HH:mm) become an ISO instant for the backend, and the backend's "Thu, 29 Oct 2026 09:30:00 IST" strings come back
 * as a date and a time again.
 */

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** "09:30" as "09:30 AM". */
export function timeLabel(time) {
  const [hours, minutes] = time.split(':').map(Number);
  return `${String(hours % 12 || 12).padStart(2, '0')}:${String(minutes).padStart(2, '0')} ${hours < 12 ? 'AM' : 'PM'}`;
}

/** Every half hour of the day as Select options ({ value: "09:30", label: "09:30 AM" }). */
export const TIME_OPTIONS = Array.from({ length: 48 }, (_, i) => {
  const value = `${String(Math.floor(i / 2)).padStart(2, '0')}:${i % 2 ? '30' : '00'}`;
  return { value, label: timeLabel(value) };
});

/** A date and time in Indian time as an ISO instant, or null while either is missing. */
export function toInstant(date, time) {
  if (!date || !time) return null;
  return new Date(`${date}T${time}:00+05:30`).toISOString();
}

/** A backend date ("Thu, 29 Oct 2026 09:30:00 IST") as { date: "2026-10-29", time: "09:30" }; null if unreadable. */
export function apiDateTimeParts(value) {
  const match = /(\d{2}) (\w{3}) (\d{4}) (\d{2}):(\d{2})/.exec(value ?? '');
  if (!match) return null;
  const [, day, month, year, hours, minutes] = match;
  const monthNumber = MONTHS.indexOf(month) + 1;
  if (!monthNumber) return null;
  return { date: `${year}-${String(monthNumber).padStart(2, '0')}-${day}`, time: `${hours}:${minutes}` };
}

/** The start and end (ISO instants) of a day (yyyy-MM-dd) in Indian time. */
export function dayBounds(date) {
  const start = toInstant(date, '00:00');
  return { from: start, to: new Date(new Date(start).getTime() + 24 * 3600 * 1000).toISOString() };
}

/** Today in Indian time as yyyy-MM-dd. */
export function todayInIndia() {
  return new Date(Date.now() + 5.5 * 3600 * 1000).toISOString().slice(0, 10);
}
