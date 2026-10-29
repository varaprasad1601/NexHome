import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import Pagination from '../common/Pagination';
import { MoneyBadge } from './moneyParts';
import { headClass, linkClass } from '../system/systemStyles';
import { useAdminAuth } from '../../../context/adminAuth';
import { formatAmount, serviceNameOf } from '../../../data/bookingOptions';
import { PAYMENT_LOOKUPS } from '../../../data/paymentOptions';
import useLookupLabels from '../../../hooks/useLookupLabels';
import { splitApiDateTime } from '../../../utils/format';

const PERIODS = [
  { key: 'TODAY', label: 'Today', phrase: 'today' },
  { key: 'WEEK', label: 'This Week', phrase: 'this week' },
  { key: 'MONTH', label: 'This Month', phrase: 'this month' },
  { key: 'ALL', label: 'Overall', phrase: 'overall' }
];
const PAGE_SIZE = 5;
const EMPTY_PAGE = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };

/** "08 Oct 2026, 02:29 PM", or a dash. */
const when = (value) => {
  const parts = splitApiDateTime(value);
  return parts ? `${parts.date}, ${parts.time}` : '—';
};

/** A signed-in GET of a paged list; reloads when `path` changes. */
function usePage(path) {
  const { authRequest } = useAdminAuth();
  const [state, setState] = useState({ data: null, loading: true, error: '' });
  useEffect(() => {
    let cancelled = false;
    authRequest(path)
      .then((data) => !cancelled && setState({ data, loading: false, error: '' }))
      .catch((err) => !cancelled && setState({ data: null, loading: false, error: err.message }));
    return () => {
      cancelled = true;
    };
  }, [authRequest, path]);
  return state;
}

/**
 * The partner profile's Earnings tab (PAYOUT_MANAGE read), the period switcher of
 * Admin Sreens/earnings-samples/partner-earnings-period-switcher.html: Today (default), This Week, This Month or
 * Overall picks the period (IST, by when the job was completed; GET /api/payouts/earnings/{id}?period=): the earnings
 * in large type with the job count, the jobs, total amount, commission and partner earnings, then the details in two
 * parts: the period's jobs (booking id, payment method, total, commission, partner earnings; 5 to a page) and the
 * partner's payouts (GET /api/payouts?partnerProfileId=, the most recent 5 to a page).
 */
export default function PartnerEarnings({ partnerProfileId }) {
  const { label } = useLookupLabels(PAYMENT_LOOKUPS);
  const [period, setPeriod] = useState('TODAY');
  const [recordsPage, setRecordsPage] = useState(0);
  const [payoutsPage, setPayoutsPage] = useState(0);
  const earnings = usePage(`/api/payouts/earnings/${partnerProfileId}?period=${period}&page=${recordsPage}&size=${PAGE_SIZE}`);
  const payouts = usePage(`/api/payouts?partnerProfileId=${partnerProfileId}&page=${payoutsPage}&size=${PAGE_SIZE}`);
  const summary = earnings.data;
  const phrase = PERIODS.find((item) => item.key === period).phrase;
  const records = summary?.records ?? EMPTY_PAGE;
  const payoutPage = payouts.data ?? EMPTY_PAGE;

  const choose = (key) => {
    setPeriod(key);
    setRecordsPage(0);
  };

  return (
    <div className="space-y-5 pt-3">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div className="text-[2.5rem] font-black leading-none text-[var(--navy)]">
            {summary ? formatAmount(summary.earnings) : <span className="block h-10 w-40 animate-pulse rounded-lg bg-[var(--light)]" />}
          </div>
          <div className="mt-1.5 text-[0.8125rem] font-bold text-[var(--muted)]">
            {summary ? `earned ${phrase} · ${summary.jobs} job${summary.jobs === 1 ? '' : 's'}` : ' '}
          </div>
        </div>
        <div className="inline-flex rounded-xl bg-[var(--light)] p-1" role="tablist" aria-label="Period">
          {PERIODS.map((item) => (
            <button
              key={item.key}
              type="button"
              role="tab"
              aria-selected={period === item.key}
              onClick={() => choose(item.key)}
              className={`rounded-lg px-4 py-2 text-sm font-bold transition ${
                period === item.key ? 'bg-white text-[var(--blue)] shadow-[0_1px_3px_rgba(14,51,89,0.12)]' : 'text-[var(--muted)] hover:text-[var(--ink)]'
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-2 overflow-hidden rounded-xl border border-[var(--line)] md:grid-cols-4">
        {[
          { label: 'Jobs Completed', value: summary?.jobs, className: 'text-[var(--ink)]' },
          { label: 'Total Amount', value: summary && formatAmount(summary.totalAmount), className: 'text-[var(--ink)]' },
          { label: 'Commission', value: summary && `− ${formatAmount(summary.commission)}`, className: 'text-[var(--muted)]' },
          { label: 'Partner Earnings', value: summary && formatAmount(summary.earnings), className: 'text-[var(--green-2)]' }
        ].map((figure, i) => (
          <div key={figure.label} className={`px-4 py-3.5 ${i ? 'border-l border-[var(--line)]' : ''}`}>
            <div className="text-[0.75rem] font-bold text-[var(--muted)]">{figure.label}</div>
            <div className={`mt-0.5 text-xl font-black ${figure.className}`}>{figure.value ?? '—'}</div>
          </div>
        ))}
      </div>

      {earnings.error && (
        <div role="alert" className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
          {earnings.error}
        </div>
      )}

      <div className="grid grid-cols-1 items-stretch gap-5 xl:grid-cols-[minmax(0,1.65fr)_minmax(0,1fr)]">
        {/* The period's jobs; both parts as tall as the taller one, their page controls at the bottom */}
        <section className="flex flex-col">
          <h3 className="mb-3 flex items-baseline justify-between gap-3 text-base font-black text-[var(--navy)]">
            Earnings Details
            <span className="text-xs font-bold text-[var(--muted)]">
              {summary && `${summary.jobs} completed ${period === 'ALL' ? 'in all' : phrase}`}
            </span>
          </h3>
          <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
            <table className="w-full min-w-[38rem] text-sm">
              <thead className="bg-[var(--light)]">
                <tr>
                  <th className={headClass}>Booking ID</th>
                  <th className={headClass}>Payment</th>
                  <th className={`${headClass} text-right`}>Total Amount</th>
                  <th className={`${headClass} text-right`}>Commission</th>
                  <th className={`${headClass} text-right`}>Partner Earnings</th>
                </tr>
              </thead>
              <tbody>
                {earnings.loading && (
                  <tr className="border-t border-[var(--line)]">
                    <td colSpan={5} className="px-3 py-4">
                      <div className="h-4 animate-pulse rounded bg-[var(--light)]" />
                    </td>
                  </tr>
                )}
                {!earnings.loading && records.content.length === 0 && (
                  <tr className="border-t border-[var(--line)]">
                    <td colSpan={5} className="px-3 py-10 text-center text-sm text-[var(--muted)]">
                      No jobs completed {phrase} yet.
                    </td>
                  </tr>
                )}
                {!earnings.loading &&
                  records.content.map((record) => (
                    <tr key={record.bookingId} className="border-t border-[var(--line)] align-top text-[var(--ink)]">
                      <td className="px-3 py-3">
                        <Link to={`/admin/bookings/${record.bookingId}`} className={linkClass}>
                          {record.bookingPublicId}
                        </Link>
                        <div className="mt-0.5 max-w-[16rem] truncate text-xs text-[var(--muted)]" title={serviceNameOf(record.serviceName)}>
                          {serviceNameOf(record.serviceName)}
                        </div>
                        <div className="whitespace-nowrap text-xs text-[var(--muted)]">{when(record.completedAt)}</div>
                      </td>
                      <td className="px-3 py-3">
                        <span
                          className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${
                            record.paymentMethod === 'CASH' ? 'bg-[#E9F6EF] text-[var(--green-2)]' : 'bg-[#EDF6FD] text-[var(--blue)]'
                          }`}
                        >
                          {label('PAYMENT_METHOD', record.paymentMethod)}
                        </span>
                      </td>
                      <td className="whitespace-nowrap px-3 py-3 text-right">{formatAmount(record.totalAmount)}</td>
                      <td className="whitespace-nowrap px-3 py-3 text-right text-[var(--muted)]">− {formatAmount(record.commission)}</td>
                      <td className="whitespace-nowrap px-3 py-3 text-right font-black text-[var(--green-2)]">
                        {formatAmount(record.partnerEarnings)}
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
          {!earnings.loading && (
            <div className="mt-auto min-h-[3.25rem]">
              <Pagination pageData={records} noun="jobs" onPageChange={setRecordsPage} />
            </div>
          )}
        </section>

        {/* The partner's payouts */}
        <section className="flex flex-col">
          <h3 className="mb-3 flex items-baseline justify-between gap-3 text-base font-black text-[var(--navy)]">
            Payout Details
            <span className="text-xs font-bold text-[var(--muted)]">Most recent first</span>
          </h3>
          {payouts.error ? (
            <div role="alert" className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
              {payouts.error}
            </div>
          ) : payouts.loading ? (
            <div className="h-40 animate-pulse rounded-xl bg-[var(--light)]" />
          ) : payoutPage.content.length === 0 ? (
            <p className="mb-0 rounded-xl border border-[var(--line)] px-4 py-8 text-center text-sm text-[var(--muted)]">
              No payouts yet: they come with completed, paid online jobs.
            </p>
          ) : (
            <ul className="m-0 list-none divide-y divide-[var(--line)] rounded-xl border border-[var(--line)] p-0">
              {payoutPage.content.map((payout) => (
                <li key={payout.payoutId} className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-1 px-4 py-3">
                  <span className="flex flex-wrap items-center gap-2">
                    <span className="font-black text-[var(--ink)]">{payout.publicId}</span>
                    <MoneyBadge status={payout.payoutStatus} label={label('PAYOUT_STATUS', payout.payoutStatus)} />
                  </span>
                  <span className="text-right text-base font-black text-[var(--green-2)]">{formatAmount(payout.netAmount)}</span>
                  <span className="text-xs text-[var(--muted)]">
                    Booking{' '}
                    <Link to={`/admin/bookings/${payout.bookingId}`} className={linkClass}>
                      {payout.bookingPublicId}
                    </Link>
                  </span>
                  <span className="text-right text-xs text-[var(--muted)]">
                    {payout.paidAt ? `Paid ${when(payout.paidAt)}` : when(payout.creationDate)}
                  </span>
                </li>
              ))}
            </ul>
          )}
          {!payouts.loading && !payouts.error && (
            <div className="mt-auto min-h-[3.25rem]">
              <Pagination pageData={payoutPage} noun="payouts" onPageChange={setPayoutsPage} />
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
