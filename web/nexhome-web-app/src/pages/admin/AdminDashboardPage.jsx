import { Calendar, ChevronDown } from 'lucide-react';
import { useEffect, useMemo } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import StatCard from '../../components/admin/dashboard/StatCard';
import { STAT_CARDS } from '../../data/adminDashboardData';
import { useAdminAuth } from '../../context/adminAuth';
import useUserStats, { toCardFigures } from '../../hooks/useUserStats';
import { formatDay, formatNumber } from '../../utils/format';

/**
 * The admin home: the headline figures for the current month. Customers and partners are live from the database;
 * the other cards are sample data until those modules exist.
 */
export default function AdminDashboardPage() {
  const { stats, error } = useUserStats();
  const { can } = useAdminAuth();

  useEffect(() => {
    document.title = 'Dashboard | NexHome Admin';
  }, []);

  const { monthStart, monthEnd } = useMemo(() => {
    const today = new Date();
    return {
      monthStart: new Date(today.getFullYear(), today.getMonth(), 1),
      monthEnd: new Date(today.getFullYear(), today.getMonth() + 1, 0)
    };
  }, []);

  // A card with `fn` (the live customers and partners) is shown only with read access to it
  const cards = STAT_CARDS.filter((card) => !card.fn || can(card.fn, 'READ')).map((card) => {
    if (!card.source) return card;
    // A failed request, or a reply without this card's figures
    if (error || (stats && !stats[card.source])) {
      return { ...card, value: null, failed: true, change: null, trend: null, note: 'Could not load live figures', noteTitle: error };
    }
    if (!stats) return { ...card, value: null, change: null, trend: null, note: null };
    const figures = toCardFigures(stats[card.source]);
    return { ...card, ...figures, note: `+${formatNumber(figures.added)} this month` };
  });

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Dashboard' }, { label: 'Overview' }]}
        title="Dashboard Overview"
        description="Here's what's happening with your NexHome platform today."
        actions={
          <button
            type="button"
            className="flex items-center gap-3 rounded-xl border border-[var(--line)] bg-white px-4 py-2.5 text-sm font-bold text-[var(--ink)] shadow-[0_1px_2px_rgba(14,51,89,0.04)] hover:border-[#C7E3FC]"
          >
            <Calendar size={15} className="text-[var(--blue)]" />
            {formatDay(monthStart)} - {formatDay(monthEnd)}
            <ChevronDown size={14} className="text-[var(--muted)]" />
          </button>
        }
      />

      {/* Always one row: five columns on wide screens, a sideways-scrolling strip on smaller ones */}
      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:grid xl:grid-cols-5 xl:overflow-visible xl:px-0 xl:pb-0">
        {cards.map((stat) => (
          <StatCard key={stat.key} stat={stat} />
        ))}
      </div>
    </div>
  );
}
