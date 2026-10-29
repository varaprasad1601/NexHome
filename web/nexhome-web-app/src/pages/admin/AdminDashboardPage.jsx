import { Calendar, ChevronDown } from 'lucide-react';
import { useEffect, useMemo } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import StatCard from '../../components/admin/dashboard/StatCard';
import { STAT_CARDS } from '../../data/adminDashboardData';
import { useAdminAuth } from '../../context/adminAuth';
import useStats, { toCardFigures } from '../../hooks/useStats';
import { formatDay, formatNumber, formatRupees } from '../../utils/format';

/** Where each live card's figures come from: the stats reply it reads, and its figures in that reply (null: none). */
const SOURCES = {
  customers: {
    stats: 'users',
    figures: ({ customers: c }) => c && toCardFigures(c.total, c.addedThisMonth, c.months)
  },
  partners: {
    stats: 'users',
    figures: ({ partners: p }) => p && toCardFigures(p.total, p.addedThisMonth, p.months)
  },
  bookings: { stats: 'bookings', figures: (s) => toCardFigures(s.total, s.addedThisMonth, s.months) },
  revenue: {
    stats: 'payments',
    figures: (s) => toCardFigures(s.revenueAmount, s.revenueThisMonth, s.revenueMonths)
  },
  // The average partner rating of published reviews; change against the average at the end of last month
  rating: {
    stats: 'reviews',
    figures: (s) => {
      const months = s.months.filter((month) => month.averageRating != null);
      const now = s.averagePartnerRating == null ? null : Number(s.averagePartnerRating);
      const previous = s.months.length > 1 ? s.months[s.months.length - 2].averageRating : null;
      return {
        value: now,
        failed: now == null,
        change: now != null && previous ? Math.round(((now - previous) / previous) * 1000) / 10 : null,
        trend: months.length > 1 ? months.map((month) => Number(month.averageRating)) : null,
        reviews: s.published
      };
    }
  }
};

/**
 * The admin home: the headline figures for the current month, live from the database: customers and partners
 * (GET /api/users/stats), bookings (/api/bookings/stats) and revenue (/api/payments/stats: payments captured plus
 * cash collected, less refunds) and the average partner rating (/api/reviews/stats).
 */
export default function AdminDashboardPage() {
  const { can } = useAdminAuth();
  const replies = {
    users: useStats('/api/users/stats', can('CUSTOMER_MANAGE', 'READ') || can('PARTNER_MANAGE', 'READ')),
    bookings: useStats('/api/bookings/stats', can('BOOKING_MANAGE', 'READ')),
    payments: useStats('/api/payments/stats', can('PAYMENT_MANAGE', 'READ')),
    reviews: useStats('/api/reviews/stats', can('REVIEW_MANAGE', 'READ'))
  };

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

  // A card with `fn` (every live one) is shown only with read access to it
  const cards = STAT_CARDS.filter((card) => !card.fn || can(card.fn, 'READ')).map((card) => {
    if (!card.source) return card;
    const source = SOURCES[card.source];
    const { stats, error } = replies[source.stats];
    if (!stats && !error) return { ...card, value: null, change: null, trend: null, note: null };
    const figures = stats && source.figures(stats);
    // A failed request, or a reply without this card's figures
    if (!figures) {
      return { ...card, value: null, failed: true, change: null, trend: null, note: 'Could not load live figures', noteTitle: error };
    }
    if (card.source === 'rating') {
      return {
        ...card,
        ...figures,
        note: figures.reviews ? `Based on ${formatNumber(figures.reviews)} review${figures.reviews === 1 ? '' : 's'}` : 'No reviews yet'
      };
    }
    if (card.currency) {
      // Refunds can outweigh a month's income
      const added = Math.round(figures.added);
      const note = `${added < 0 ? '−' : '+'}${formatRupees(Math.abs(added))} this month`;
      return { ...card, ...figures, value: Math.round(figures.value), note };
    }
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
