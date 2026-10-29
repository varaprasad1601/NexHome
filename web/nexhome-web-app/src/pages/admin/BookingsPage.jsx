import {
  CalendarCheck,
  CalendarClock,
  CircleCheck,
  CircleX,
  Clock,
  Hourglass,
  Plus,
  RotateCcw,
  Search
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import BookingsTable from '../../components/admin/booking/BookingsTable';
import CreateBookingModal from '../../components/admin/booking/CreateBookingModal';
import Select from '../../components/admin/common/Select';
import {
  cardClass,
  primaryButtonClass,
  resetButtonClass,
  searchInputClass
} from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { BOOKING_LOOKUPS } from '../../data/bookingOptions';
import useDebounced from '../../hooks/useDebounced';
import useLookupLabels from '../../hooks/useLookupLabels';
import { formatNumber } from '../../utils/format';
import { dayBounds, todayInIndia } from '../../utils/schedule';

/** The tiles: all bookings, today's, and the main stages (counts from GET /api/bookings/stats). */
const STAT_TILES = [
  { key: 'total', label: 'All Bookings', icon: CalendarCheck, tile: 'bg-[#EDF6FD] text-[var(--blue)]' },
  { key: 'today', label: 'Scheduled Today', icon: CalendarClock, tile: 'bg-[#E8EEF6] text-[var(--navy)]' },
  {
    key: 'waiting',
    label: 'Awaiting Partner',
    icon: Hourglass,
    tile: 'bg-[#FFF7DE] text-[#B7860B]',
    statuses: ['PENDING', 'CONFIRMED']
  },
  {
    key: 'active',
    label: 'In Progress',
    icon: Clock,
    tile: 'bg-[#E9F6EF] text-[var(--green)]',
    statuses: ['ASSIGNED', 'PARTNER_EN_ROUTE', 'IN_PROGRESS', 'PAUSED']
  },
  {
    key: 'completed',
    label: 'Completed',
    icon: CircleCheck,
    tile: 'bg-[#E9F6EF] text-[var(--green-2)]',
    statuses: ['COMPLETED']
  },
  {
    key: 'cancelled',
    label: 'Cancelled',
    icon: CircleX,
    tile: 'bg-[#FDECEC] text-[#E53E3E]',
    statuses: ['CANCELLED']
  }
];

/**
 * Bookings > All Bookings (/admin/bookings, BOOKING_MANAGE read): counts, search (booking id, service, customer or
 * partner name / id), status and payment filters, and the paged list (`booking/BookingsTable`); the booking id opens
 * the booking. With write, Create Booking books a service for a customer (`booking/CreateBookingModal`).
 */
export default function BookingsPage() {
  const { authRequest, can } = useAdminAuth();
  const navigate = useNavigate();
  const canWrite = can('BOOKING_MANAGE', 'WRITE');
  const { values } = useLookupLabels(BOOKING_LOOKUPS);
  const [searchText, setSearchText] = useState('');
  const search = useDebounced(searchText.trim());
  const [bookingStatus, setBookingStatus] = useState('');
  const [paymentStatus, setPaymentStatus] = useState('');
  const [stats, setStats] = useState(null);
  const [creating, setCreating] = useState(false);
  // The Scheduled Today tile shows only today's bookings (by scheduled start, IST)
  const [todayOnly, setTodayOnly] = useState(false);

  useEffect(() => {
    document.title = 'Bookings | NexHome Admin';
    authRequest('/api/bookings/stats')
      .then(setStats)
      .catch(() => setStats(null));
  }, [authRequest]);

  const count = (tile) => {
    if (!stats) return null;
    if (tile.key === 'total') return stats.total;
    if (tile.key === 'today') return stats.scheduledToday;
    return tile.statuses.reduce((sum, status) => sum + (stats.byStatus[status] ?? 0), 0);
  };
  const reset = () => {
    setSearchText('');
    setBookingStatus('');
    setPaymentStatus('');
    setTodayOnly(false);
  };
  const options = (type, all) => [
    { value: '', label: all },
    ...(values[type] ?? []).map((value) => ({ value: value.valueCode, label: value.displayName }))
  ];
  const onCreated = useCallback(
    (booking) =>
      navigate(`/admin/bookings/${booking.bookingId}`, {
        state: { toast: { message: `Booking ${booking.publicId} created.`, tone: 'success' } }
      }),
    [navigate]
  );

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Bookings' }, { label: 'All Bookings' }]}
        title="Bookings"
        description="Every booking, from checkout to completion: assign partners, follow the work and settle problems."
        actions={
          canWrite && (
            <button type="button" className={primaryButtonClass} onClick={() => setCreating(true)}>
              <Plus size={17} strokeWidth={2.4} />
              Create Booking
            </button>
          )
        }
      />

      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:grid xl:grid-cols-6 xl:overflow-visible xl:px-0 xl:pb-0">
        {STAT_TILES.map((tile) => {
          const Icon = tile.icon;
          const value = count(tile);
          return (
            <div
              key={tile.key}
              {...(tile.key === 'today'
                ? {
                    role: 'button',
                    tabIndex: 0,
                    title: "Show only today's bookings",
                    onClick: () => setTodayOnly((on) => !on),
                    onKeyDown: (e) => e.key === 'Enter' && setTodayOnly((on) => !on)
                  }
                : {})}
              className={`flex min-w-[12rem] flex-1 items-center gap-3 xl:min-w-0 ${cardClass} ${
                tile.key === 'today' && todayOnly ? 'ring-2 ring-[var(--blue)]' : ''
              }`}
            >
              <span className={`flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full ${tile.tile}`}>
                <Icon size={20} strokeWidth={2} />
              </span>
              <div>
                <div className="text-[0.8125rem] font-bold text-[var(--muted)]">{tile.label}</div>
                <div className="mt-1 h-7 text-[1.5rem] font-black leading-7 text-[var(--ink)]">
                  {value === null ? (
                    <span className="block h-full w-10 animate-pulse rounded-md bg-[var(--light)]" />
                  ) : (
                    formatNumber(value)
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_12rem_12rem_auto]">
          <label className="relative">
            <span className="sr-only">Search bookings</span>
            <Search
              size={16}
              className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]"
            />
            <input
              type="search"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              placeholder="Search by booking ID, service, customer or partner..."
              className={searchInputClass}
            />
          </label>
          <Select
            ariaLabel="Booking status"
            value={bookingStatus}
            onChange={setBookingStatus}
            options={options('BOOKING_STATUS', 'All Status')}
          />
          <Select
            ariaLabel="Payment status"
            value={paymentStatus}
            onChange={setPaymentStatus}
            options={options('BOOKING_PAYMENT_STATUS', 'All Payments')}
          />
          <button type="button" className={resetButtonClass} onClick={reset}>
            <RotateCcw size={15} />
            Reset
          </button>
        </div>
        {todayOnly && (
          <div className="mb-3 flex items-center justify-between rounded-xl bg-[#E8EEF6] px-4 py-2.5 text-sm font-bold text-[var(--navy)]">
            Showing bookings scheduled for today
            <button type="button" onClick={() => setTodayOnly(false)} className="text-[var(--blue)]">
              Show all
            </button>
          </div>
        )}
        <BookingsTable
          params={{ search, bookingStatus, paymentStatus, ...(todayOnly ? dayBounds(todayInIndia()) : {}) }}
          emptyText={todayOnly ? 'No bookings are scheduled for today.' : 'No bookings match your search or filters.'}
        />
      </section>

      {creating && <CreateBookingModal onClose={() => setCreating(false)} onCreated={onCreated} />}
    </div>
  );
}
