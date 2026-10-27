import { useEffect, useState } from 'react';

import { useAdminAuth } from '../context/adminAuth';

/** Customer and partner counts from GET /api/users/stats: { stats, error } (both null while loading). */
export default function useUserStats() {
  const { authRequest } = useAdminAuth();
  const [state, setState] = useState({ stats: null, error: null });

  useEffect(() => {
    let cancelled = false;
    authRequest('/api/users/stats')
      .then((stats) => {
        if (!cancelled) setState({ stats, error: null });
      })
      .catch((err) => {
        if (!cancelled) setState({ stats: null, error: err.message });
      });
    return () => {
      cancelled = true;
    };
  }, [authRequest]);

  return state;
}

/**
 * The stat card figures of one role: total, change of the total since the end of last month (null when there were
 * none then), this month's additions and the 10 monthly totals.
 */
export function toCardFigures(roleStats) {
  const months = roleStats.months;
  const previous = months.length > 1 ? months[months.length - 2].total : 0;
  return {
    value: roleStats.total,
    change: previous ? Math.round(((roleStats.total - previous) / previous) * 1000) / 10 : null,
    added: roleStats.addedThisMonth,
    trend: months.map((month) => month.total)
  };
}
