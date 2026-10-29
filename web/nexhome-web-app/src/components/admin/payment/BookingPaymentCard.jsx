import { CreditCard } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import FormModal from '../common/FormModal';
import { MoneyBadge, When } from './moneyParts';
import { cardClass, linkClass } from '../system/systemStyles';
import { useAdminAuth } from '../../../context/adminAuth';
import { formatAmount } from '../../../data/bookingOptions';
import { PAYMENT_LOOKUPS } from '../../../data/paymentOptions';
import useLookupLabels from '../../../hooks/useLookupLabels';

const smallButton =
  'rounded-lg px-3 py-1.5 text-[0.8125rem] font-bold transition disabled:cursor-not-allowed disabled:opacity-50';

/**
 * The booking page's Payment card (GET /api/payments/bookings/{id}): payments with refunds, the cash collection and
 * the partner's payout. With PAYMENT_MANAGE write: Record Payment for an unpaid online booking (creates the checkout's
 * order, then captures it through the MANUAL gateway, with an optional reference such as a UPI id), Refund a captured
 * payment (amount and reason, never more than is left) and settle cash (Mark Collected). `onChanged` reloads the
 * booking (its payment status and history change).
 */
export default function BookingPaymentCard({ booking, onChanged, onMessage }) {
  const { authRequest, can } = useAdminAuth();
  const canWrite = can('PAYMENT_MANAGE', 'WRITE');
  const { label } = useLookupLabels(PAYMENT_LOOKUPS);
  const [money, setMoney] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [dialog, setDialog] = useState(null); // { kind: 'record' } | { kind: 'refund', payment }
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    authRequest(`/api/payments/bookings/${booking.bookingId}`)
      .then((loaded) => !cancelled && setMoney(loaded))
      .catch(
        () => !cancelled && setMoney({ orders: [], payments: [], refunds: [], cashCollection: null, payout: null })
      );
    return () => {
      cancelled = true;
    };
  }, [authRequest, booking.bookingId, booking.paymentStatus, booking.bookingStatus, reloadKey]);

  const changed = useCallback(
    (message) => {
      onMessage(message);
      setReloadKey((key) => key + 1);
      onChanged();
    },
    [onChanged, onMessage]
  );

  const recordPayment = async (formValues) => {
    const order = await authRequest('/api/payments/orders/create', {
      method: 'POST',
      body: { bookingId: booking.bookingId }
    });
    await authRequest('/api/payments/orders/capture', {
      method: 'POST',
      body: { paymentOrderId: order.paymentOrderId, providerPaymentId: formValues.reference.trim() || null }
    });
    setDialog(null);
    changed(`Payment of ${formatAmount(order.amount)} recorded.`);
  };
  const refund = async (formValues) => {
    await authRequest('/api/payments/refunds/create', {
      method: 'POST',
      body: { paymentId: dialog.payment.paymentId, amount: Number(formValues.amount), reason: formValues.reason.trim() }
    });
    setDialog(null);
    changed(`Refunded ${formatAmount(formValues.amount)}.`);
  };
  const collectCash = async () => {
    setBusy(true);
    try {
      await authRequest('/api/payments/cash-collections/update', {
        method: 'PUT',
        body: { cashCollectionId: money.cashCollection.cashCollectionId, status: 'COLLECTED' }
      });
      changed('Cash marked as collected.');
    } catch (err) {
      onMessage(err.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  const unpaidOnline =
    booking.paymentMethod === 'ONLINE' &&
    ['PENDING', 'FAILED'].includes(booking.paymentStatus) &&
    booking.bookingStatus !== 'CANCELLED';
  const cash = money?.cashCollection;
  const payout = money?.payout;

  return (
    <section className={cardClass}>
      <div className="mb-3 flex items-center gap-2">
        <CreditCard size={17} className="text-[var(--blue)]" />
        <h2 className="mb-0 flex-1 text-[1.0625rem] font-black text-[var(--navy)]">Payment</h2>
        {canWrite && unpaidOnline && (
          <button
            type="button"
            onClick={() => setDialog({ kind: 'record' })}
            className={`${smallButton} bg-[var(--green)] text-white hover:bg-[var(--green-2)]`}
          >
            Record Payment
          </button>
        )}
      </div>
      {!money ? (
        <div className="h-16 animate-pulse rounded-xl bg-[var(--light)]" />
      ) : (
        <div className="space-y-3 text-sm">
          {money.payments.length === 0 && !cash && (
            <p className="mb-0 text-[var(--muted)]">
              {booking.paymentMethod === 'CASH'
                ? 'Paid in cash to the partner once the work is done.'
                : unpaidOnline
                  ? 'Waiting for the online payment.'
                  : 'No payments.'}
            </p>
          )}
          {money.payments.map((payment) => {
            // The transaction fee is the platform's: never refunded
            const left =
              Number(payment.amount) - Number(booking.transactionFeeAmount ?? 0) - Number(payment.refundedAmount ?? 0);
            return (
              <div
                key={payment.paymentId}
                className="flex flex-wrap items-center gap-3 rounded-xl border border-[var(--line)] px-3.5 py-2.5"
              >
                <div className="min-w-0 flex-1">
                  <div className="font-bold text-[var(--ink)]">
                    {formatAmount(payment.amount)}
                    <span className="ml-2 font-normal text-[var(--muted)]">
                      {booking.publicId}
                      {payment.providerPaymentId && ` · ${payment.providerPaymentId}`}
                    </span>
                  </div>
                  <div className="text-[0.8125rem] text-[var(--muted)]">
                    {payment.capturedAt ? <When value={payment.capturedAt} /> : payment.failureMessage}
                    {Number(payment.refundedAmount) > 0 && ` · Refunded ${formatAmount(payment.refundedAmount)}`}
                  </div>
                </div>
                <MoneyBadge status={payment.status} label={label('PAYMENT_STATUS', payment.status)} />
                {canWrite && ['CAPTURED', 'PARTIALLY_REFUNDED'].includes(payment.status) && left > 0 && (
                  <button
                    type="button"
                    onClick={() => setDialog({ kind: 'refund', payment, left })}
                    className={`${smallButton} border border-[#F5C2C2] text-[#C53030] hover:bg-[#FDECEC]`}
                  >
                    Refund
                  </button>
                )}
              </div>
            );
          })}
          {money.refunds.map((item) => (
            <div key={item.refundId} className="flex items-center justify-between gap-3 text-[0.8125rem]">
              <span className="text-[var(--muted)]">
                Refund {formatAmount(item.amount)}
                {item.reason && ` · ${item.reason}`}
              </span>
              <MoneyBadge status={item.status} label={label('REFUND_STATUS', item.status)} />
            </div>
          ))}
          {cash && (
            <div className="flex flex-wrap items-center gap-3 rounded-xl border border-[var(--line)] px-3.5 py-2.5">
              <div className="min-w-0 flex-1">
                <div className="font-bold text-[var(--ink)]">Cash {formatAmount(cash.amount)}</div>
                <div className="text-[0.8125rem] text-[var(--muted)]">
                  {cash.confirmedAt ? <When value={cash.confirmedAt} /> : 'To be collected by the partner'}
                  {cash.customerAcknowledgedAt && ' · confirmed by the customer'}
                </div>
              </div>
              <MoneyBadge status={cash.status} label={label('CASH_COLLECTION_STATUS', cash.status)} />
              {canWrite && ['DUE', 'DISPUTED'].includes(cash.status) && (
                <button
                  type="button"
                  disabled={busy}
                  onClick={collectCash}
                  className={`${smallButton} bg-[var(--green)] text-white hover:bg-[var(--green-2)]`}
                >
                  Mark Collected
                </button>
              )}
            </div>
          )}
          {payout && (
            <div className="rounded-xl bg-[var(--light)] px-3.5 py-2.5">
              <div className="flex items-center justify-between gap-3">
                <span className="font-bold text-[var(--ink)]">Partner payout {formatAmount(payout.netAmount)}</span>
                <MoneyBadge status={payout.payoutStatus} label={label('PAYOUT_STATUS', payout.payoutStatus)} />
              </div>
              <div className="text-[0.8125rem] text-[var(--muted)]">
                {formatAmount(payout.grossAmount)} less {formatAmount(payout.platformCommission)} commission
                {Number(payout.adjustmentsAmount) !== 0 && `, adjusted ${formatAmount(payout.adjustmentsAmount)}`}
                {can('PAYOUT_MANAGE', 'READ') && (
                  <>
                    {' · '}
                    <Link to="/admin/payments/payouts" className={linkClass}>
                      Payouts
                    </Link>
                  </>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {dialog?.kind === 'record' && (
        <FormModal
          title="Record Payment"
          fields={[
            {
              key: 'reference',
              label: 'Payment Reference',
              type: 'text',
              maxLength: 150,
              hint: 'E.g. the UPI transaction id. The MANUAL gateway records the payment as captured at once.'
            }
          ]}
          initial={{ reference: '' }}
          submitLabel="Record Payment"
          onSubmit={recordPayment}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === 'refund' && (
        <FormModal
          title="Refund Payment"
          fields={[
            {
              key: 'amount',
              label: `Amount (₹, at most ${dialog.left})`,
              type: 'text',
              required: true,
              validate: (value) =>
                !/^\d+(\.\d{1,2})?$/.test(value.trim()) || Number(value) <= 0
                  ? 'Enter an amount'
                  : Number(value) > dialog.left
                    ? `At most ${dialog.left}`
                    : null
            },
            { key: 'reason', label: 'Reason', type: 'textarea', rows: 3, required: true, maxLength: 2000, wide: true }
          ]}
          initial={{ amount: String(dialog.left), reason: '' }}
          submitLabel="Refund"
          onSubmit={refund}
          onClose={() => setDialog(null)}
        />
      )}
    </section>
  );
}
