import { useEffect, useState } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import MonthPicker from '../../components/admin/dashboard/MonthPicker';
import StatCard from '../../components/admin/dashboard/StatCard';
import { STAT_CARDS } from '../../data/adminDashboardData';
import { useAdminAuth } from '../../context/adminAuth';
import useStats, { currentMonthInIndia, monthLabel, shiftMonth, toMonthFigures } from '../../hooks/useStats';
import { formatNumber, formatRupees } from '../../utils/format';

/** The statistics cover the last 10 months (this one included), so those are the months that can be chosen. */
const STATS_MONTHS = 10;

/**
 * Where each live card's figures come from: the stats reply it reads, and its figures in that reply as of the chosen
 * month (`YYYY-MM`; null: none). Every reply carries the last 10 months, so no other request is needed.
 */
const SOURCES = {
  customers: { stats: 'users', figures: ({ customers: c }, month) => c && toMonthFigures(c.months, month) },
  partners: { stats: 'users', figures: ({ partners: p }, month) => p && toMonthFigures(p.months, month) },
  bookings: { stats: 'bookings', figures: (s, month) => toMonthFigures(s.months, month) },
  revenue: { stats: 'payments', figures: (s, month) => toMonthFigures(s.revenueMonths, month) },
  // The average partner rating of published reviews up to the month's end; change against the month before
  rating: {
    stats: 'reviews',
    figures: (s, month) => {
      const index = s.months.findIndex((row) => row.month === month);
      if (index < 0) return null;
      const upTo = s.months.slice(0, index + 1);
      const now = upTo[index].averageRating == null ? null : Number(upTo[index].averageRating);
      const previous = index > 0 ? upTo[index - 1].averageRating : null;
      const rated = upTo.filter((row) => row.averageRating != null);
      return {
        value: now,
        failed: now == null,
        change: now != null && previous ? Math.round(((now - previous) / previous) * 1000) / 10 : null,
        trend: rated.length > 1 ? rated.map((row) => Number(row.averageRating)) : null,
        // Published reviews made up to the month's end (this month: every published one)
        reviews: index === s.months.length - 1 ? s.published : upTo.reduce((sum, row) => sum + Number(row.reviews ?? 0), 0)
      };
    }
  }
};

/**
 * The admin home: the headline figures for a month (this one by default; the month picker goes back over the 10 months
 * the statistics cover), live from the database: customers and partners
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

  const thisMonth = currentMonthInIndia();
  const firstMonth = shiftMonth(thisMonth, -(STATS_MONTHS - 1));
  const [month, setMonth] = useState(thisMonth);
  const current = month === thisMonth;
  const inMonth = current ? 'this month' : `in ${monthLabel(month)}`;

  // A card with `fn` (every live one) is shown only with read access to it
  const cards = STAT_CARDS.filter((card) => !card.fn || can(card.fn, 'READ')).map((card) => {
    if (!card.source) return card;
    const source = SOURCES[card.source];
    const { stats, error } = replies[source.stats];
    if (!stats && !error) return { ...card, value: null, change: null, trend: null, note: null };
    const figures = stats && source.figures(stats, month);
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
      const note = `${added < 0 ? '−' : '+'}${formatRupees(Math.abs(added))} ${inMonth}`;
      return { ...card, ...figures, value: Math.round(figures.value), note };
    }
    return { ...card, ...figures, note: `+${formatNumber(figures.added)} ${inMonth}` };
  });

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Dashboard' }, { label: 'Overview' }]}
        title="Dashboard Overview"
        description={
          current
            ? "Here's what's happening with your NexHome platform today."
            : `How NexHome stood at the end of ${monthLabel(month)}.`
        }
        actions={<MonthPicker value={month} min={firstMonth} max={thisMonth} onChange={setMonth} />}
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
