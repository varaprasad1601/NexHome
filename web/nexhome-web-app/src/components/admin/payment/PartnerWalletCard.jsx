import { useCallback, useEffect, useState } from 'react';

import ConfirmDialog from '../system/ConfirmDialog';
import { useAdminAuth } from '../../../context/adminAuth';
import { formatAmount } from '../../../data/bookingOptions';
import { splitApiDateTime } from '../../../utils/format';

/**
 * The partner's wallet (V60, GET /api/payouts/wallet/{id}) above the Earnings tab: what is not paid out yet, the
 * online jobs NexHome received (after commission) less the commission on the cash jobs the partner kept. With
 * PAYOUT_MANAGE write, Pay Out Wallet sends it in one transfer (POST /api/payouts/wallet/settle), which empties it.
 */
export default function PartnerWalletCard({ partnerProfileId, onPaid }) {
  const { authRequest, can } = useAdminAuth();
  const [wallet, setWallet] = useState(null);
  const [error, setError] = useState('');
  const [confirming, setConfirming] = useState(false);

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
  const owe = balance < 0;
  const since = splitApiDateTime(wallet.since);
  const figures = [
    ['Online jobs', `${wallet.onlineJobs} · ${formatAmount(wallet.onlineAmount)}`],
    ['Commission on online', `− ${formatAmount(wallet.onlineCommission)}`],
    ['Cash kept by partner', `${wallet.cashJobs} · ${formatAmount(wallet.cashAmount)}`],
    ['Commission on cash', `− ${formatAmount(wallet.cashCommission)}`]
  ];

  return (
    <section className={`rounded-2xl border p-5 ${owe ? 'border-amber-200 bg-amber-50' : 'border-emerald-200 bg-emerald-50'}`}>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className={`text-[0.8125rem] font-extrabold ${owe ? 'text-amber-700' : 'text-emerald-700'}`}>
            {owe ? 'Partner owes NexHome' : 'Wallet · NexHome pays the partner'}
          </div>
          <div className="mt-1 text-[2rem] font-black leading-none text-[var(--navy)]">{formatAmount(Math.abs(balance))}</div>
          <div className="mt-1.5 text-[0.75rem] font-semibold text-[var(--muted)]">
            {since ? `Since the last payout on ${since.date}` : 'No wallet payout yet'}
            {wallet.onHoldJobs ? ` · ${formatAmount(wallet.onHoldAmount)} on hold, not included` : ''}
          </div>
        </div>
        {can('PAYOUT_MANAGE', 'WRITE') ? (
          <button
            type="button"
            disabled={balance <= 0}
            onClick={() => setConfirming(true)}
            className="rounded-xl bg-[var(--green)] px-4 py-2.5 text-[0.8125rem] font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            Pay Out Wallet
          </button>
        ) : null}
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        {figures.map(([term, value]) => (
          <div key={term} className="rounded-xl bg-white/70 px-3 py-2.5">
            <dt className="text-[0.6875rem] font-bold text-[var(--muted)]">{term}</dt>
            <dd className="mt-0.5 text-[0.875rem] font-extrabold text-[var(--ink)]">{value}</dd>
          </div>
        ))}
      </dl>
      {confirming ? (
        <ConfirmDialog
          title="Pay out the wallet?"
          message={`${formatAmount(balance)} goes to the partner's verified primary bank account in one transfer, settling ${wallet.items.length} job${wallet.items.length === 1 ? '' : 's'}. The wallet then starts again at ₹0.`}
          confirmLabel="Pay Out"
          onClose={() => setConfirming(false)}
          onConfirm={async () => {
            await authRequest('/api/payouts/wallet/settle', { method: 'POST', body: { partnerProfileId } });
            setConfirming(false);
            load();
            onPaid?.();
          }}
        />
      ) : null}
    </section>
  );
}
