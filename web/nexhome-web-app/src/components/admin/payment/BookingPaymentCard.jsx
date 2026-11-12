import { CreditCard } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import FormModal from '../common/FormModal';
import { MoneyBadge, When } from './moneyParts';
import { cardClass, linkClass } from '../system/systemStyles';
import { useAdminAuth } from '../../../context/adminAuth';
import { formatAmount } from '../../../data/bookingOptions';
import { PAYMENT_LOOKUPS, paymentModeLabel } from '../../../data/paymentOptions';
import useLookupLabels from '../../../hooks/useLookupLabels';

const smallButton =
  'rounded-lg px-3 py-1.5 text-[0.8125rem] font-bold transition disabled:cursor-not-allowed disabled:opacity-50';

/** One label and value of the card: the label on the left, the value right-aligned. */
function Line({ name, children }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <span className="shrink-0 text-[var(--muted)]">{name}</span>
      <span className="min-w-0 break-words text-right font-semibold text-[var(--ink)]">{children}</span>
    </div>
  );
}

/**
 * The booking page's Payment card (GET /api/payments/bookings/{id}): payments with refunds, the cash collection and
 * the partner's payout; how each payment was made (UPI, card, ... for Cashfree). With PAYMENT_MANAGE write: Check Payment
 * for an open Cashfree order (asks the gateway, POST /api/payments/orders/verify), Record Payment for an unpaid online booking (creates the checkout's
 * order, then captures it through the MANUAL gateway, with an optional reference such as a UPI id), Refund a captured
 * payment (amount and reason, never more than is left) and settle cash (Mark Collected). `onChanged` reloads the
 * booking (its payment status and history change).
 */
export default function BookingPaymentCard({ booking, version, onChanged, onMessage }) {
  const { authRequest, can } = useAdminAuth();
  const canWrite = can('PAYMENT_MANAGE', 'WRITE');
  const { label } = useLookupLabels(PAYMENT_LOOKUPS);
  const [money, setMoney] = useState(null);
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
    // Once per page load (`version`: the booking page's reload count), so an action here or the page's Refresh loads
    // it once
  }, [authRequest, booking.bookingId, version]);

  const changed = useCallback(
    (message) => {
      onMessage(message);
      // The page reloads, which reloads this card with it
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
  // An online order through the gateway (Cashfree): ask it whether the customer has paid (records it if so)
  const [checking, setChecking] = useState(false);
  const openGatewayOrder = (money?.orders ?? []).find(
    (order) => order.provider !== 'MANUAL' && ['CREATED', 'PENDING'].includes(order.paymentStatus)
  );
  const checkPayment = async () => {
    setChecking(true);
    try {
      const order = await authRequest('/api/payments/orders/verify', {
        method: 'POST',
        body: { paymentOrderId: openGatewayOrder.paymentOrderId }
      });
      changed(order.paymentStatus === 'PAID' ? 'Payment received through Cashfree.' : 'The customer has not paid yet.');
    } catch (err) {
      onMessage(err.message, 'error');
    } finally {
      setChecking(false);
    }
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
        {canWrite && unpaidOnline && openGatewayOrder && (
          <button
            type="button"
            disabled={checking}
            onClick={checkPayment}
            className={`${smallButton} border border-[var(--line)] bg-white text-[var(--ink)] hover:bg-[var(--light)] disabled:opacity-60`}
          >
            {checking ? 'Checking…' : 'Check Payment'}
          </button>
        )}
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
            // An online payment fee (bookings before V68) is never refunded
            const left =
              Number(payment.amount) - Number(booking.transactionFeeAmount ?? 0) - Number(payment.refundedAmount ?? 0);
            return (
              <div key={payment.paymentId} className="rounded-xl border border-[var(--line)] px-3.5 py-3">
                <div className="mb-2 flex items-center gap-2">
                  <span className="flex-1 text-[1rem] font-black text-[var(--ink)]">{formatAmount(payment.amount)}</span>
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
                <div className="space-y-1.5 border-t border-[var(--line)] pt-2 text-[0.8125rem]">
                  <Line name="Booking">{booking.publicId}</Line>
                  {payment.providerPaymentId && <Line name="Reference ID">{payment.providerPaymentId}</Line>}
                  <Line name="Paid Via">{paymentModeLabel(payment)}</Line>
                  {payment.capturedAt ? (
                    <Line name="Captured">
                      <When value={payment.capturedAt} />
                    </Line>
                  ) : (
                    payment.failureMessage && <Line name="Reason">{payment.failureMessage}</Line>
                  )}
                  {Number(payment.refundedAmount) > 0 && (
                    <Line name="Refunded">{formatAmount(payment.refundedAmount)}</Line>
                  )}
                  {Number(payment.customerChargesAmount ?? 0) > 0 && (
                    // Cashfree's surcharge (V69): paid by the customer on top, not NexHome's money
                    <Line name="Charges Paid by Customer">+ {formatAmount(payment.customerChargesAmount)}</Line>
                  )}
                  {payment.paymentMethod === 'ONLINE' && payment.provider !== 'MANUAL' && (
                    <>
                      <Line name="Gateway Charges">
                        {payment.gatewayChargesAmount == null ? (
                          <span className="font-normal text-[var(--muted)]">Not reported yet</span>
                        ) : (
                          formatAmount(payment.gatewayChargesAmount)
                        )}
                      </Line>
                      {payment.gatewayChargesAmount != null && (
                        <Line name="NexHome Receives">
                          {formatAmount(Number(payment.amount) - Number(payment.gatewayChargesAmount))}
                        </Line>
                      )}
                    </>
                  )}
                </div>
              </div>
            );
          })}
          {money.refunds.map((item) => (
            <div key={item.refundId} className="flex items-start gap-3 rounded-xl bg-[#FDF6F6] px-3.5 py-2.5 text-[0.8125rem]">
              <div className="min-w-0 flex-1">
                <div className="font-bold text-[var(--ink)]">Refund {formatAmount(item.amount)}</div>
                {item.reason && <div className="text-[var(--muted)]">{item.reason}</div>}
              </div>
              <MoneyBadge status={item.status} label={label('REFUND_STATUS', item.status)} />
            </div>
          ))}
          {cash && (
            <div className="rounded-xl border border-[var(--line)] px-3.5 py-3">
              <div className="mb-2 flex items-center gap-2">
                <span className="flex-1 text-[1rem] font-black text-[var(--ink)]">Cash {formatAmount(cash.amount)}</span>
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
              <div className="space-y-1.5 border-t border-[var(--line)] pt-2 text-[0.8125rem]">
                <Line name="Collected">
                  {cash.confirmedAt ? (
                    <When value={cash.confirmedAt} />
                  ) : (
                    <span className="font-normal text-[var(--muted)]">To be collected by the partner</span>
                  )}
                </Line>
                {cash.customerAcknowledgedAt && <Line name="Customer">Confirmed paying</Line>}
              </div>
            </div>
          )}
          {payout && (
            <div className="rounded-xl bg-[var(--light)] px-3.5 py-3">
              <div className="mb-2 flex items-center gap-2">
                <span className="flex-1 font-black text-[var(--ink)]">Partner&apos;s Share</span>
                {payout.payoutStatus === 'CANCELLED' && (
                  <MoneyBadge status={payout.payoutStatus} label={label('PAYOUT_STATUS', payout.payoutStatus)} />
                )}
              </div>
              <div className="space-y-1.5 border-t border-[var(--line)] pt-2 text-[0.8125rem]">
                <Line name="Job Amount">{formatAmount(payout.grossAmount)}</Line>
                <Line name="Commission">− {formatAmount(payout.platformCommission)}</Line>
                {Number(payout.adjustmentsAmount) !== 0 && (
                  <Line name="Adjustments">{formatAmount(payout.adjustmentsAmount)}</Line>
                )}
                <Line name="Partner Gets">{formatAmount(payout.netAmount)}</Line>
                {can('PAYOUT_MANAGE', 'READ') && (
                  <div className="text-right">
                    <Link to="/admin/payments/payouts" className={linkClass}>
                      View Payouts
                    </Link>
                  </div>
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
