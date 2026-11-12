import { useCallback, useEffect, useState } from 'react';

import RecordPayoutModal from './RecordPayoutModal';
import { paidOnLabel } from './payoutParts';
import { useAdminAuth } from '../../../context/adminAuth';
import { formatAmount } from '../../../data/bookingOptions';

/**
 * What NexHome owes the partner (V62, GET /api/payouts/wallet/{id}) above the Earnings tab, since joining: cash and
 * online jobs with their commission, earned, paid so far (recorded payouts), deducted (V63) and the balance. With PAYOUT_MANAGE write,
 * **Record Payout / Deduction** opens the dialog with the partner fixed.
 */
export default function PartnerWalletCard({ partnerProfileId, partnerPublicId, partnerName, onPaid }) {
  const { authRequest, can } = useAdminAuth();
  const [wallet, setWallet] = useState(null);
  const [error, setError] = useState('');
  const [recording, setRecording] = useState(false);

  const load = useCallback(() => {
    authRequest(`/api/payouts/wallet/${partnerProfileId}`)
      .then((data) => {
        setWallet(data);
        setError('');
      })
      .catch((err) => setError(err.message));
  }, [authRequest, partnerProfileId]);

  useEffect(() => {
    load();
  }, [load]);

  if (error) return <p className="rounded-xl bg-red-50 px-4 py-3 text-[0.8125rem] font-semibold text-red-700">{error}</p>;
  if (!wallet) return <div className="h-28 animate-pulse rounded-2xl bg-[var(--light)]" />;

  const balance = Number(wallet.balance);
  const over = balance < 0;
  const last = wallet.lastPayout;
  const figures = [
    ['Cash jobs', `${wallet.cashJobs} · ${formatAmount(wallet.cashAmount)}`],
    ['Online jobs', `${wallet.onlineJobs} · ${formatAmount(wallet.onlineAmount)}`],
    ['NexHome commission', `− ${formatAmount(wallet.totalCommission)}`],
    ...(Number(wallet.adjustmentsAmount) !== 0 ? [['Adjustments', formatAmount(wallet.adjustmentsAmount)]] : []),
    ['Partner earned', formatAmount(wallet.totalEarned)],
    ['Paid so far', `− ${formatAmount(wallet.paidTotal)}`],
    ...(Number(wallet.deductedTotal) > 0 ? [['Deducted', `− ${formatAmount(wallet.deductedTotal)}`]] : [])
  ];

  return (
    <section className={`rounded-2xl border p-5 ${over ? 'border-amber-200 bg-amber-50' : 'border-emerald-200 bg-emerald-50'}`}>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className={`text-[0.8125rem] font-extrabold ${over ? 'text-amber-700' : 'text-emerald-700'}`}>
            {over ? 'Paid more than earned' : 'NexHome owes the partner'}
          </div>
          <div className="mt-1 text-[2rem] font-black leading-none text-[var(--navy)]">{formatAmount(Math.abs(balance))}</div>
          <div className="mt-1.5 text-[0.75rem] font-semibold text-[var(--muted)]">
            {last
              ? `Last paid ${formatAmount(last.amount)} on ${paidOnLabel(last.paidOn)} · ${wallet.payoutCount} payout${wallet.payoutCount === 1 ? '' : 's'}`
              : 'No payout recorded yet'}
          </div>
        </div>
        {can('PAYOUT_MANAGE', 'WRITE') ? (
          <button
            type="button"
            disabled={balance <= 0}
            onClick={() => setRecording(true)}
            className="rounded-xl bg-[var(--green)] px-4 py-2.5 text-[0.8125rem] font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            Record Payout / Deduction
          </button>
        ) : null}
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-3 lg:grid-cols-4 xl:grid-cols-7">
        {figures.map(([term, value]) => (
          <div key={term} className="rounded-xl bg-white/70 px-3 py-2.5">
            <dt className="text-[0.6875rem] font-bold text-[var(--muted)]">{term}</dt>
            <dd className="mt-0.5 text-[0.875rem] font-extrabold text-[var(--ink)]">{value}</dd>
          </div>
        ))}
      </dl>
      {recording ? (
        <RecordPayoutModal
          authRequest={authRequest}
          partner={{
            partnerProfileId,
            publicId: partnerPublicId,
            partnerName,
            totalEarned: wallet.totalEarned,
            paidTotal: wallet.paidTotal,
            deductedTotal: wallet.deductedTotal,
            balance: wallet.balance
          }}
          onClose={() => setRecording(false)}
          onSaved={() => {
            setRecording(false);
            load();
            onPaid?.();
          }}
        />
      ) : null}
    </section>
  );
}
