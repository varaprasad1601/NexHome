import { Banknote } from 'lucide-react';
import { useEffect, useState } from 'react';

import Modal from '../common/Modal';
import { formatAmount } from '../../../data/bookingOptions';

const MONTHS = { Jan: 0, Feb: 1, Mar: 2, Apr: 3, May: 4, Jun: 5, Jul: 6, Aug: 7, Sep: 8, Oct: 9, Nov: 10, Dec: 11 };

/** A backend date ("Thu, 06 Nov 2026 10:30:00 IST", Indian time) as milliseconds, or null. */
function apiMillis(value) {
  const match = /(\d{1,2}) (\w{3}) (\d{4}) (\d{2}):(\d{2}):(\d{2})/.exec(value ?? '');
  if (!match || MONTHS[match[2]] === undefined) return null;
  const [, day, month, year, hours, minutes, seconds] = match;
  // IST is UTC + 5:30
  return Date.UTC(Number(year), MONTHS[month], Number(day), Number(hours), Number(minutes), Number(seconds)) - 330 * 60 * 1000;
}

/** Minutes worked so far from the timer events (START / RESUME to PAUSE / STOP, an open stretch up to now), rounded up. */
function workedMinutes(detail) {
  let seconds = 0;
  let since = null;
  for (const log of detail.timeLogs ?? []) {
    const at = apiMillis(log.eventAt);
    if (at === null) return Number(detail.workedMinutes) || 0;
    if (log.eventType === 'START' || log.eventType === 'RESUME') since = at;
    else if ((log.eventType === 'PAUSE' || log.eventType === 'STOP') && since !== null) {
      seconds += (at - since) / 1000;
      since = null;
    }
  }
  if (since !== null) seconds += Math.max(0, (Date.now() - since) / 1000);
  return Math.ceil(seconds / 60);
}

/**
 * Collect payment (the partner app's screen as a dialog), opened by Finish Work on a cash job not yet paid, or by
 * Collect Payment once its work is finished. It shows the amount to collect in large type (an hourly job priced for
 * the time worked, as the backend will); nothing changes until **Cash Collected**: the timer's STOP (unless the work
 * is already finished), then the cash collection COLLECTED, which completes the booking. Back closes it.
 */
export default function CollectPaymentModal({ booking, detail, authRequest, onClose, onCollected }) {
  const [amount, setAmount] = useState(booking.totalAmount);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const finished = Boolean(booking.actualEndAt);

  useEffect(() => {
    // Not finished yet: an hourly job is priced for the minutes worked so far
    if (finished || booking.pricing?.pricingType !== 'HOURLY') return undefined;
    let cancelled = false;
    const query = new URLSearchParams({ serviceId: String(booking.serviceId), minutes: String(Math.max(1, workedMinutes(detail))) });
    if (booking.serviceVariantId) query.set('serviceVariantId', String(booking.serviceVariantId));
    authRequest(`/api/catalog/services/quote?${query}`)
      .then((quote) => !cancelled && setAmount(quote.total))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authRequest, booking, detail, finished]);

  const collect = async () => {
    setBusy(true);
    setError('');
    try {
      if (!finished) {
        await authRequest('/api/bookings/timer', { method: 'POST', body: { bookingId: booking.bookingId, eventType: 'STOP' } });
      }
      const payments = await authRequest(`/api/payments/bookings/${booking.bookingId}`);
      const cash = payments?.cashCollection;
      if (!cash) throw new Error('No cash is due for this booking.');
      if (cash.status !== 'COLLECTED') {
        await authRequest('/api/payments/cash-collections/update', {
          method: 'PUT',
          body: { cashCollectionId: cash.cashCollectionId, status: 'COLLECTED' }
        });
      }
      onCollected();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal title="Collect Payment" onClose={busy ? () => {} : onClose} width="max-w-[26rem]">
      <div className="flex flex-col items-center gap-2 py-4 text-center">
        <span className="grid h-14 w-14 place-items-center rounded-2xl bg-[#E9F6EF] text-[var(--green)]">
          <Banknote size={28} />
        </span>
        <div className="mt-2 text-[0.8125rem] font-bold text-[var(--muted)]">Collect in cash from the customer</div>
        <div className="text-[2.75rem] font-black leading-none text-[var(--navy)]">{formatAmount(amount)}</div>
        <div className="text-[0.8125rem] text-[var(--muted)]">
          {booking.publicId}
          {finished ? ' · work finished' : ' · the work is finished when the cash is collected'}
        </div>
      </div>
      {error ? <p className="mb-3 rounded-xl bg-[#FDECEC] px-3 py-2 text-[0.8125rem] font-semibold text-[#C53030]">{error}</p> : null}
      <div className="flex gap-3">
        <button
          type="button"
          disabled={busy}
          onClick={onClose}
          className="flex-1 rounded-xl border border-[var(--line)] px-4 py-3 text-[0.875rem] font-bold text-[var(--ink)] hover:bg-[var(--light)] disabled:opacity-50"
        >
          Back
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={collect}
          className="flex-1 rounded-xl bg-[var(--green)] px-4 py-3 text-[0.875rem] font-extrabold text-white hover:bg-[var(--green-2)] disabled:opacity-50"
        >
          {busy ? 'Saving…' : 'Cash Collected'}
        </button>
      </div>
    </Modal>
  );
}
