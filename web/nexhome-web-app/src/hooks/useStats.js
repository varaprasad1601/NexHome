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
