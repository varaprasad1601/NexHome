import { CircleCheck, CircleX, Clock, ListChecks, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Pagination from '../../components/admin/common/Pagination';
import Toast from '../../components/admin/common/Toast';
import { BookingLink, CustomerLink, MoneyBadge, MoneyTabs, MoneyTile, When } from '../../components/admin/payment/moneyParts';
import { cardClass, headClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { formatAmount } from '../../data/bookingOptions';
import { PAYMENT_LOOKUPS, paymentModeLabel } from '../../data/paymentOptions';
import useClientPaging from '../../hooks/useClientPaging';
import useLookupLabels from '../../hooks/useLookupLabels';

/** The gateway's attempt statuses: label and badge colours. */
const ATTEMPTS = {
  SUCCESS: { label: 'Success', className: 'bg-[#E9F6EF] text-[var(--green-2)]' },
  FAILED: { label: 'Failed', className: 'bg-[#FDECEC] text-[#C53030]' },
  PENDING: { label: 'Pending', className: 'bg-[#FFF7DE] text-[#8A6508]' },
  USER_DROPPED: { label: 'User Dropped', className: 'bg-[#EEF2F6] text-[var(--muted)]' },
  CANCELLED: { label: 'Cancelled', className: 'bg-[#EEF2F6] text-[var(--muted)]' },
  VOID: { label: 'Void', className: 'bg-[#EEF2F6] text-[var(--muted)]' },
  NOT_ATTEMPTED: { label: 'Not Attempted', className: 'bg-[#EEF2F6] text-[var(--muted)]' }
};

/** The filter tabs over the transactions, and which statuses each holds. */
const FILTERS = [
  { key: 'ALL', label: 'All' },
  { key: 'SUCCESS', label: 'Success', statuses: ['SUCCESS'] },
  { key: 'FAILED', label: 'Failed', statuses: ['FAILED'] },
  { key: 'PENDING', label: 'Pending', statuses: ['PENDING'] },
  { key: 'OTHER', label: 'Cancelled / Dropped', statuses: ['USER_DROPPED', 'CANCELLED', 'VOID', 'NOT_ATTEMPTED'] }
];

function AttemptBadge({ status }) {
  const look = ATTEMPTS[status] ?? { label: status, className: 'bg-[#EEF2F6] text-[var(--muted)]' };
  return <span className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${look.className}`}>{look.label}</span>;
}

function Detail({ label, children }) {
  return (
    <div className="min-w-0">
      <div className="text-[0.75rem] font-bold uppercase tracking-[0.05em] text-[var(--muted)]">{label}</div>
      <div className="mt-1 break-words text-sm text-[var(--ink)]">{children}</div>
    </div>
  );
}

/**
 * One payment order (`/admin/payments/customers/orders/:paymentOrderId`, from Customer Payments > Payment Orders;
 * PAYMENT_MANAGE read): the order with its customer and bookings, then every transaction tried against it at the
 * gateway (V67: success, failed, pending, cancelled or dropped, newest first, as on Cashfree's dashboard: payment id,
 * status, amount, method, bank reference, message, when), filterable by status, and the payments captured from it.
 * With PAYMENT_MANAGE write, Refresh from Cashfree fetches the attempts again (POST /api/payments/orders/sync; an open
 * order is also captured if paid).
 */
export default function PaymentOrderPage() {
  const { paymentOrderId } = useParams();
  const { authRequest, can } = useAdminAuth();
  const { label } = useLookupLabels(PAYMENT_LOOKUPS);
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [syncing, setSyncing] = useState(false);
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);

  useEffect(() => {
    let cancelled = false;
    authRequest(`/api/payments/orders/${paymentOrderId}`)
      .then((loaded) => !cancelled && setDetail(loaded))
      .catch((err) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [authRequest, paymentOrderId]);

  const order = detail?.order;
  useEffect(() => {
    document.title = `${order?.publicId ?? 'Payment Order'} | NexHome Admin`;
  }, [order?.publicId]);

  const attempts = useMemo(() => detail?.attempts ?? [], [detail]);
  const counts = useMemo(() => {
    const of = (statuses) => attempts.filter((attempt) => statuses.includes(attempt.attemptStatus)).length;
    return Object.fromEntries(FILTERS.map((item) => [item.key, item.statuses ? of(item.statuses) : attempts.length]));
  }, [attempts]);
  const shown = useMemo(() => {
    const chosen = FILTERS.find((item) => item.key === filter);
    return chosen?.statuses ? attempts.filter((attempt) => chosen.statuses.includes(attempt.attemptStatus)) : attempts;
  }, [attempts, filter]);
  const { pageRows, pageData, setPage } = useClientPaging(shown, 10);
  // The successful attempt carries what the gateway took and its settlement (V68)
  const paid = useMemo(() => attempts.find((attempt) => attempt.attemptStatus === 'SUCCESS') ?? null, [attempts]);
  const charges =
    paid?.chargesRecordedAt != null ? Number(paid.gatewayCharge ?? 0) + Number(paid.gatewayTax ?? 0) : null;

  const sync = async () => {
    setSyncing(true);
    try {
      const loaded = await authRequest('/api/payments/orders/sync', { method: 'POST', body: { paymentOrderId: Number(paymentOrderId) } });
      setDetail(loaded);
      setToast({ message: `Transactions refreshed from ${loaded.order.provider === 'CASHFREE' ? 'Cashfree' : loaded.order.provider}.`, tone: 'success', id: Date.now() });
    } catch (err) {
      setToast({ message: err.message, tone: 'error', id: Date.now() });
    } finally {
      setSyncing(false);
    }
  };

  const crumbs = [
    { label: 'Payments & Payouts' },
    { label: 'Customer Payments', to: '/admin/payments/customers?tab=orders' },
    { label: order?.publicId ?? '' }
  ];

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={crumbs}
        title={order ? `Payment Order ${order.publicId}` : ''}
        description="Every transaction tried against this order at the payment gateway: successful, failed, pending and cancelled."
        actions={
          order && can('PAYMENT_MANAGE', 'WRITE') && order.provider !== 'MANUAL' ? (
            <button
              type="button"
              onClick={sync}
              disabled={syncing}
              className="flex items-center gap-2 rounded-xl border border-[var(--line)] bg-white px-4 py-2.5 text-sm font-bold text-[var(--ink)] hover:bg-[var(--light)] disabled:opacity-60"
            >
              <RefreshCw size={15} className={syncing ? 'animate-spin' : ''} />
              {syncing ? 'Refreshing…' : `Refresh from ${order.provider === 'CASHFREE' ? 'Cashfree' : order.provider}`}
            </button>
          ) : null
        }
      />

      {error ? (
        <div className={`${cardClass} text-sm text-[#C53030]`}>{error}</div>
      ) : !order ? (
        <div className={`${cardClass} h-40 animate-pulse`} />
      ) : (
        <>
          <section className={`${cardClass} mb-5`}>
            <div className="mb-5 flex flex-wrap items-center gap-3">
              <span className="text-[1.75rem] font-black text-[var(--navy)]">{formatAmount(order.amount)}</span>
              <MoneyBadge status={order.paymentStatus} label={label('PAYMENT_ORDER_STATUS', order.paymentStatus)} />
            </div>
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
              <Detail label="Order ID">{order.publicId}</Detail>
              <Detail label="Gateway Order ID">{order.providerOrderId ?? '—'}</Detail>
              <Detail label="Gateway">{order.provider === 'MANUAL' ? 'Recorded by staff' : order.provider === 'CASHFREE' ? 'Cashfree' : order.provider}</Detail>
              <Detail label="Customer">
                <CustomerLink id={order.customerProfileId} name={order.customerName} />
              </Detail>
              <Detail label={order.bookings?.length > 1 ? 'Bookings' : 'Booking'}>
                {order.bookings?.length ? (
                  <span className="flex flex-wrap gap-x-3">
                    {order.bookings.map((booking) => (
                      <BookingLink key={booking.bookingId} id={booking.bookingId} publicId={booking.publicId} />
                    ))}
                  </span>
                ) : (
                  '—'
                )}
              </Detail>
              <Detail label="Created">
                <When value={order.creationDate} />
              </Detail>
              <Detail label="Expires">
                <When value={order.expiresAt} />
              </Detail>
            </div>
          </section>

          <div className="mb-5 flex gap-4 overflow-x-auto pb-1 xl:overflow-visible">
            <MoneyTile icon={ListChecks} tone="bg-[#E8EEF6] text-[var(--navy)]" label="Transactions" value={String(counts.ALL)} />
            <MoneyTile icon={CircleCheck} tone="bg-[#E9F6EF] text-[var(--green-2)]" label="Successful" value={String(counts.SUCCESS)} />
            <MoneyTile icon={CircleX} tone="bg-[#FDECEC] text-[#C53030]" label="Failed" value={String(counts.FAILED)} />
            <MoneyTile icon={Clock} tone="bg-[#FFF7DE] text-[#B7860B]" label="Pending" value={String(counts.PENDING)} />
          </div>

          {paid && order.provider !== 'MANUAL' && (
            <section className={`${cardClass} mb-5`}>
              <h2 className="mb-1 text-[1.0625rem] font-black text-[var(--navy)]">Gateway Charges &amp; Settlement</h2>
              <p className="mb-4 text-sm text-[var(--muted)]">
                {charges == null
                  ? 'The gateway has not reported its charges for this payment yet. They arrive by webhook or are fetched every hour; Refresh asks now.'
                  : 'What the gateway kept from the payment and what it pays NexHome. Customers are not charged for it.'}
              </p>
              <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
                <Detail label="Amount Paid">{formatAmount(paid.amount)}</Detail>
                <Detail label="Gateway Charge">{paid.chargesRecordedAt ? formatAmount(paid.gatewayCharge ?? 0) : '—'}</Detail>
                <Detail label="GST on Charge">{paid.chargesRecordedAt ? formatAmount(paid.gatewayTax ?? 0) : '—'}</Detail>
                <Detail label="Total Charges">{charges == null ? '—' : formatAmount(charges)}</Detail>
                <Detail label="NexHome Receives">
                  {paid.settlementAmount != null
                    ? formatAmount(paid.settlementAmount)
                    : charges != null
                      ? formatAmount(Number(paid.amount) - charges)
                      : '—'}
                </Detail>
                <Detail label="Settlement ID">{paid.settlementId ?? 'Not settled yet'}</Detail>
                <Detail label="Bank UTR">{paid.settlementUtr ?? '—'}</Detail>
                <Detail label="Settled On">{paid.settledAt ? <When value={paid.settledAt} /> : '—'}</Detail>
              </div>
            </section>
          )}

          <section className={`${cardClass} mb-5`}>
            <h2 className="mb-3 text-[1.0625rem] font-black text-[var(--navy)]">Transactions</h2>
            <MoneyTabs
              tabs={FILTERS.map((item) => ({ key: item.key, label: `${item.label} (${counts[item.key]})` }))}
              active={filter}
              onChange={(key) => {
                setFilter(key);
                setPage(0);
              }}
            />
            {shown.length === 0 ? (
              <p className="mb-0 py-6 text-center text-sm text-[var(--muted)]">
                {attempts.length === 0
                  ? order.provider === 'MANUAL'
                    ? 'This order was paid by a payment recorded by staff.'
                    : 'No transactions yet: the customer has not tried to pay. Refresh to fetch them from the gateway.'
                  : 'No transactions with this status.'}
              </p>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-[var(--line)]">
                        <th className={headClass}>#</th>
                        <th className={headClass}>Payment ID</th>
                        <th className={headClass}>Status</th>
                        <th className={headClass}>Amount</th>
                        <th className={headClass}>Payment Method</th>
                        <th className={headClass}>Bank Reference</th>
                        <th className={headClass}>Message</th>
                        <th className={headClass}>Attempted</th>
                        <th className={headClass}>Completed</th>
                      </tr>
                    </thead>
                    <tbody>
                      {pageRows.map((attempt, i) => (
                        <tr key={attempt.attemptId} className="border-b border-[var(--line)] align-top last:border-0">
                          <td className="px-3 py-3 text-[var(--muted)]">{pageData.page * pageData.size + i + 1}</td>
                          <td className="px-3 py-3 font-bold text-[var(--ink)]">{attempt.providerPaymentId}</td>
                          <td className="px-3 py-3">
                            <AttemptBadge status={attempt.attemptStatus} />
                          </td>
                          <td className="whitespace-nowrap px-3 py-3 font-black">
                            {formatAmount(attempt.amount)}
                            {Number(attempt.surchargeAmount ?? 0) + Number(attempt.surchargeTax ?? 0) > 0 && (
                              <span className="block text-xs font-normal text-[var(--muted)]">
                                incl. {formatAmount(Number(attempt.surchargeAmount ?? 0) + Number(attempt.surchargeTax ?? 0))} charges paid by customer
                              </span>
                            )}
                          </td>
                          <td className="px-3 py-3">
                            <span className="block font-bold">{paymentModeLabel(attempt)}</span>
                            {attempt.paymentMethodDetail && (
                              <span className="block max-w-[14rem] truncate text-xs text-[var(--muted)]" title={attempt.paymentMethodDetail}>
                                {attempt.paymentMethodDetail}
                              </span>
                            )}
                          </td>
                          <td className="px-3 py-3 text-[var(--muted)]">{attempt.bankReference ?? '—'}</td>
                          <td className="max-w-[18rem] px-3 py-3">
                            {attempt.message ? (
                              <span className={attempt.attemptStatus === 'FAILED' ? 'text-[#C53030]' : 'text-[var(--ink)]'}>{attempt.message}</span>
                            ) : (
                              <span className="text-[var(--muted)]">—</span>
                            )}
                            {attempt.errorCode && <span className="block text-xs text-[var(--muted)]">{attempt.errorCode}</span>}
                          </td>
                          <td className="px-3 py-3">
                            <When value={attempt.attemptedAt} />
                          </td>
                          <td className="px-3 py-3">
                            <When value={attempt.completedAt} />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <Pagination pageData={pageData} noun="transactions" onPageChange={setPage} />
              </>
            )}
          </section>

          <section className={cardClass}>
            <h2 className="mb-3 text-[1.0625rem] font-black text-[var(--navy)]">Payments Captured</h2>
            {detail.payments.length === 0 ? (
              <p className="mb-0 text-sm text-[var(--muted)]">Nothing captured from this order yet.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-[var(--line)]">
                      <th className={headClass}>Booking</th>
                      <th className={headClass}>Reference ID</th>
                      <th className={headClass}>Paid Via</th>
                      <th className={headClass}>Amount</th>
                      <th className={headClass}>Gateway Charges</th>
                      <th className={headClass}>Status</th>
                      <th className={headClass}>Captured</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.payments.map((payment) => (
                      <tr key={payment.paymentId} className="border-b border-[var(--line)] last:border-0">
                        <td className="px-3 py-3">
                          <BookingLink id={payment.bookingId} publicId={payment.bookingPublicId} />
                        </td>
                        <td className="px-3 py-3 text-[var(--muted)]">{payment.providerPaymentId ?? '—'}</td>
                        <td className="px-3 py-3 font-bold">{paymentModeLabel(payment)}</td>
                        <td className="px-3 py-3 font-black">{formatAmount(payment.amount)}</td>
                        <td className="px-3 py-3 text-[var(--muted)]">
                          {payment.gatewayChargesAmount == null
                            ? order.provider === 'MANUAL' ? 'None' : 'Not reported yet'
                            : formatAmount(payment.gatewayChargesAmount)}
                        </td>
                        <td className="px-3 py-3">
                          <MoneyBadge status={payment.status} label={label('PAYMENT_STATUS', payment.status)} />
                        </td>
                        <td className="px-3 py-3">
                          <When value={payment.capturedAt} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
