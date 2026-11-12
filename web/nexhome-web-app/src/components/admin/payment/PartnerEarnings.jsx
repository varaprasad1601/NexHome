import { CircleAlert } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import InfoTip from '../common/InfoTip';
import Pagination from '../common/Pagination';
import PartnerWalletCard from './PartnerWalletCard';
import { paidOnLabel, payoutModeLabel } from './payoutParts';
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
const LOOKUPS = [...PAYMENT_LOOKUPS, 'PAYOUT_MODE', 'DEDUCTION_REASON'];
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
 * received (paid by the customers) in large type with the job count and, in small text, what customers have not paid
 * yet (pendingEarnings / pendingJobs, as the partner app shows it), then the jobs, commission and partner earnings
 * (no total amount), then the details in two
 * parts: the period's jobs (booking id, payment method, total, commission, partner earnings; 5 to a page) and the
 * payouts and deductions recorded for the partner (GET /api/payouts/manual and /api/payouts/deductions
 * ?partnerProfileId=, merged newest first, 5 to a page). Above it
 * all what NexHome owes the partner (PartnerWalletCard) with Record Payout.
 */
export default function PartnerEarnings({ partnerProfileId, partnerPublicId, partnerName }) {
  const { label } = useLookupLabels(LOOKUPS);
  const [period, setPeriod] = useState('TODAY');
  const [recordsPage, setRecordsPage] = useState(0);
  const [payoutsPage, setPayoutsPage] = useState(0);
  // Bumped after a payout is recorded, so the payouts list loads again
  const [paidCount, setPaidCount] = useState(0);
  const earnings = usePage(`/api/payouts/earnings/${partnerProfileId}?period=${period}&page=${recordsPage}&size=${PAGE_SIZE}`);
  // Payouts and deductions together, newest first (a partner's are few: the latest 100 of each, paged here)
  const payouts = usePage(`/api/payouts/manual?partnerProfileId=${partnerProfileId}&size=100&v=${paidCount}`);
  const deductions = usePage(`/api/payouts/deductions?partnerProfileId=${partnerProfileId}&size=100&v=${paidCount}`);
  const summary = earnings.data;
  const phrase = PERIODS.find((item) => item.key === period).phrase;
  const records = summary?.records ?? EMPTY_PAGE;
  const entries = [
    ...(payouts.data?.content ?? []).map((item) => ({ kind: 'PAYOUT', key: `p${item.payoutId}`, day: item.paidOn, item })),
    ...(deductions.data?.content ?? []).map((item) => ({ kind: 'DEDUCTION', key: `d${item.deductionId}`, day: item.deductedOn, item }))
  ].sort((a, b) => (b.day ?? '').localeCompare(a.day ?? '') || (b.item.creationDate ?? '').localeCompare(a.item.creationDate ?? ''));
  const historyLoading = payouts.loading || deductions.loading;
  const historyError = payouts.error || deductions.error;
  const payoutPage = {
    content: entries.slice(payoutsPage * PAGE_SIZE, (payoutsPage + 1) * PAGE_SIZE),
    page: payoutsPage,
    size: PAGE_SIZE,
    totalElements: entries.length,
    totalPages: Math.ceil(entries.length / PAGE_SIZE)
  };

  const choose = (key) => {
    setPeriod(key);
    setRecordsPage(0);
  };

  return (
    <div className="space-y-5 pt-3">
      <PartnerWalletCard
        partnerProfileId={partnerProfileId}
        partnerPublicId={partnerPublicId}
        partnerName={partnerName}
        onPaid={() => setPaidCount((count) => count + 1)}
      />
      {/* The wallet (since joining) apart from the period's earnings below */}
      <hr className="!my-12 border-0 border-t border-[var(--line)]" />
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div className="text-[2.5rem] font-black leading-none text-[var(--navy)]">
            {summary ? formatAmount(summary.receivedEarnings) : <span className="block h-10 w-40 animate-pulse rounded-lg bg-[var(--light)]" />}
          </div>
          <div className="mt-1.5 text-[0.8125rem] font-bold text-[var(--muted)]">
            {summary ? `received ${phrase} · ${summary.jobs} job${summary.jobs === 1 ? '' : 's'}` : ' '}
          </div>
          {summary && Number(summary.pendingJobs) > 0 && (
            <div className="mt-1 text-[0.75rem] font-semibold text-[#B7860B]">
              + {formatAmount(summary.pendingEarnings)} pending · {summary.pendingJobs} job{summary.pendingJobs === 1 ? '' : 's'} not paid by the
              customer yet
            </div>
          )}
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

      <div className="grid grid-cols-3 overflow-hidden rounded-xl border border-[var(--line)]">
        {[
          { label: 'Jobs Completed', value: summary?.jobs, className: 'text-[var(--ink)]' },
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
                        {/* An online job the customer has not paid yet (no payout): its share is still pending */}
                        {!record.settlement && (
                          <span className="ml-1.5">
                            <InfoTip
                              text="Customer has not paid yet"
                              icon={CircleAlert}
                              iconClassName="text-[#D69E2E] hover:text-[#B7860B] focus-visible:text-[#B7860B]"
                            />
                          </span>
                        )}
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
          {historyError ? (
            <div role="alert" className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]">
              {historyError}
            </div>
          ) : historyLoading ? (
            <div className="h-40 animate-pulse rounded-xl bg-[var(--light)]" />
          ) : payoutPage.content.length === 0 ? (
            <p className="mb-0 rounded-xl border border-[var(--line)] px-4 py-8 text-center text-sm text-[var(--muted)]">
              No payouts or deductions recorded yet.
            </p>
          ) : (
            <ul className="m-0 list-none divide-y divide-[var(--line)] rounded-xl border border-[var(--line)] p-0">
              {payoutPage.content.map(({ kind, key, item }) => {
                const deduction = kind === 'DEDUCTION';
                const cancelled = (deduction ? item.status : item.payoutStatus) === 'CANCELLED';
                return (
                  <li key={key} className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-1 px-4 py-3">
                    <span className="flex flex-wrap items-center gap-2">
                      <span className="font-black text-[var(--ink)]">{item.publicId}</span>
                      {deduction && (
                        <span className="rounded-full bg-[#FDECEC] px-2 py-0.5 text-[0.6875rem] font-bold text-[#C53030]">Deduction</span>
                      )}
                      {cancelled && (
                        <span title={item.cancellationReason} className="rounded-full bg-[#F1F4F8] px-2 py-0.5 text-[0.6875rem] font-bold text-[var(--muted)]">
                          Cancelled
                        </span>
                      )}
                    </span>
                    <span
                      className={`text-right text-base font-black ${
                        cancelled ? 'text-[var(--muted)] line-through' : deduction ? 'text-[#C53030]' : 'text-[var(--green-2)]'
                      }`}
                    >
                      {deduction ? '− ' : ''}
                      {formatAmount(item.amount)}
                    </span>
                    <span className="min-w-0 truncate text-xs text-[var(--muted)]" title={deduction ? item.notes ?? '' : item.transactionId ?? ''}>
                      {deduction
                        ? `${label('DEDUCTION_REASON', item.deductionReason)}${item.notes ? ` · ${item.notes}` : ''}`
                        : `${payoutModeLabel(label, item.paymentMode)}${item.transactionId ? ` · ${item.transactionId}` : ''}`}
                    </span>
                    <span className="whitespace-nowrap text-right text-xs text-[var(--muted)]">
                      {deduction ? 'Deducted' : 'Paid'} {paidOnLabel(item.paidOn ?? item.deductedOn)}
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
          {!historyLoading && !historyError && (
            <div className="mt-auto min-h-[3.25rem]">
              <Pagination pageData={payoutPage} noun="entries" onPageChange={setPayoutsPage} />
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
