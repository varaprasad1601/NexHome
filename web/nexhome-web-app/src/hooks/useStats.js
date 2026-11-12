import { useEffect, useState } from 'react';

import { useAdminAuth } from '../context/adminAuth';

/** A stats endpoint's reply, fetched once `enabled`: { stats, error } (both null while loading or not enabled). */
export default function useStats(path, enabled = true) {
  const { authRequest } = useAdminAuth();
  const [state, setState] = useState({ stats: null, error: null });

  useEffect(() => {
    if (!enabled) return undefined;
    let cancelled = false;
    authRequest(path)
      .then((stats) => {
        if (!cancelled) setState({ stats, error: null });
      })
      .catch((err) => {
        if (!cancelled) setState({ stats: null, error: err.message });
      });
    return () => {
      cancelled = true;
    };
  }, [authRequest, path, enabled]);

  return state;
}

/**
 * The stat card figures of a running total: the total, its change since the end of last month (null when it was
 * 0 then), this month's additions and the monthly totals (oldest first, ending with this month).
 */
export function toCardFigures(total, addedThisMonth, months) {
  const previous = months.length > 1 ? Number(months[months.length - 2].total) : 0;
  return {
    value: Number(total),
    change: previous ? Math.round(((total - previous) / previous) * 1000) / 10 : null,
    added: Number(addedThisMonth),
    trend: months.map((month) => Number(month.total))
  };
}

/**
 * The same figures as of a chosen month (`YYYY-MM`, one of `months`): the total at that month's end, its change
 * against the month before, that month's additions and the monthly totals up to it; null when the month is not there.
 */
export function toMonthFigures(months, month) {
  const index = months.findIndex((row) => row.month === month);
  if (index < 0) return null;
  const value = Number(months[index].total);
  const previous = index > 0 ? Number(months[index - 1].total) : 0;
  return {
    value,
    change: previous ? Math.round(((value - previous) / previous) * 1000) / 10 : null,
    added: Number(months[index].added),
    trend: months.slice(0, index + 1).map((row) => Number(row.total))
  };
}

/** This month in India, `YYYY-MM` (the statistics count months in IST). */
export function currentMonthInIndia() {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Kolkata' }).slice(0, 7);
}

/** `YYYY-MM` moved by `delta` months. */
export function shiftMonth(month, delta) {
  const [year, number] = month.split('-').map(Number);
  const date = new Date(year, number - 1 + delta, 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** `YYYY-MM` as "01 Oct 2026 - 31 Oct 2026". */
export function monthRangeLabel(month) {
  const [year, number] = month.split('-').map(Number);
  const last = new Date(year, number, 0).getDate();
  const name = MONTH_NAMES[number - 1];
  return `01 ${name} ${year} - ${String(last).padStart(2, '0')} ${name} ${year}`;
}

/** `YYYY-MM` as "Oct 2026". */
export function monthLabel(month) {
  const [year, number] = month.split('-').map(Number);
  return `${MONTH_NAMES[number - 1]} ${year}`;
}
