import { CircleCheck, CircleX, Clock, PauseCircle } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import FormModal from '../../components/admin/common/FormModal';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import PagedTable from '../../components/admin/payment/PagedTable';
import { BookingLink, MoneyBadge, MoneyTile, PartnerLink, When } from '../../components/admin/payment/moneyParts';
import RowActions from '../../components/admin/system/RowActions';
import { cardClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { formatAmount } from '../../data/bookingOptions';
import { PAYMENT_LOOKUPS } from '../../data/paymentOptions';
import useLookupLabels from '../../hooks/useLookupLabels';

const TILES = [
  { status: 'PENDING', label: 'Pending', icon: Clock, tone: 'bg-[#FFF7DE] text-[#B7860B]' },
  { status: 'ON_HOLD', label: 'On Hold', icon: PauseCircle, tone: 'bg-[#E8EEF6] text-[var(--navy)]' },
  { status: 'PAID', label: 'Paid', icon: CircleCheck, tone: 'bg-[#E9F6EF] text-[var(--green-2)]' },
  { status: 'FAILED', label: 'Failed', icon: CircleX, tone: 'bg-[#FDECEC] text-[#E53E3E]' }
];

/** The actions on a payout, by status (the backend's pay and STATUS_TRANSITIONS). */
function actionsFor(payout, act) {
  const actions = [];
  if (payout.payoutStatus === 'PENDING' || payout.payoutStatus === 'FAILED') {
    actions.push({ label: 'Pay Out', onClick: () => act('pay', payout) });
  }
  if (['PENDING', 'ON_HOLD', 'FAILED'].includes(payout.payoutStatus)) {
    actions.push({ label: 'Adjust', onClick: () => act('adjust', payout) });
  }
  if (payout.payoutStatus === 'PENDING') actions.push({ label: 'Put On Hold', onClick: () => act('ON_HOLD', payout) });
  if (payout.payoutStatus === 'ON_HOLD' || payout.payoutStatus === 'FAILED') {
    actions.push({ label: 'Release', onClick: () => act('PENDING', payout) });
  }
  if (['PENDING', 'ON_HOLD', 'FAILED'].includes(payout.payoutStatus)) {
    actions.push({ label: 'Cancel Payout', danger: true, onClick: () => act('CANCELLED', payout) });
  }
  return actions;
}

/**
 * Payments & Payouts > Partner Payouts (/admin/payments/payouts, PAYOUT_MANAGE read): what partners are owed for
 * completed, paid online bookings (gross less the platform's commission plus adjustments). Tiles by status (count
 * and net); with write each payout's menu has Pay Out (to the partner's verified primary bank account, through the
 * MANUAL gateway), Adjust (a bonus or a deduction), Put On Hold / Release and Cancel Payout.
 */
export default function PayoutsPage() {
  const { authRequest, can } = useAdminAuth();
  const canWrite = can('PAYOUT_MANAGE', 'WRITE');
  const { values, label } = useLookupLabels(PAYMENT_LOOKUPS);
  const [status, setStatus] = useState('');
  const [stats, setStats] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [adjusting, setAdjusting] = useState(null);
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);

  useEffect(() => {
    document.title = 'Partner Payouts | NexHome Admin';
    authRequest('/api/payouts/stats')
      .then(setStats)
      .catch(() => setStats(null));
  }, [authRequest, reloadKey]);

  const act = async (action, payout) => {
    if (action === 'adjust') {
      setAdjusting(payout);
      return;
    }
    try {
      if (action === 'pay') {
        await authRequest('/api/payouts/pay', { method: 'PUT', body: { payoutId: payout.payoutId } });
        showToast(`${formatAmount(payout.netAmount)} paid out to ${payout.partnerName ?? payout.partnerPublicId}.`);
      } else {
        await authRequest('/api/payouts/status/update', {
          method: 'PUT',
          body: { payoutId: payout.payoutId, payoutStatus: action }
        });
        showToast(`Payout ${payout.publicId} is now ${label('PAYOUT_STATUS', action).toLowerCase()}.`);
      }
      setReloadKey((key) => key + 1);
    } catch (err) {
      showToast(err.message, 'error');
    }
  };
  const saveAdjustment = async (formValues) => {
    await authRequest('/api/payouts/adjust', {
      method: 'PUT',
      body: { payoutId: adjusting.payoutId, adjustmentsAmount: Number(formValues.adjustmentsAmount) }
    });
    setAdjusting(null);
    showToast('Payout adjusted.');
    setReloadKey((key) => key + 1);
  };

  const columns = [
    { header: 'Payout ID', cell: (row) => <span className="font-black">{row.publicId}</span> },
    { header: 'Partner', cell: (row) => <PartnerLink id={row.partnerProfileId} name={row.partnerName} /> },
    { header: 'Booking', cell: (row) => <BookingLink id={row.bookingId} publicId={row.bookingPublicId} /> },
    { header: 'Gross', cell: (row) => formatAmount(row.grossAmount) },
    {
      header: 'Commission',
      cell: (row) => <span className="text-[var(--muted)]">− {formatAmount(row.platformCommission)}</span>
    },
    {
      header: 'Adjustments',
      cell: (row) =>
        Number(row.adjustmentsAmount) === 0 ? (
          <span className="text-[var(--muted)]">—</span>
        ) : (
          formatAmount(row.adjustmentsAmount)
        )
    },
    { header: 'Net', cell: (row) => <span className="font-black">{formatAmount(row.netAmount)}</span> },
    {
      header: 'Status',
      cell: (row) => <MoneyBadge status={row.payoutStatus} label={label('PAYOUT_STATUS', row.payoutStatus)} />
    },
    { header: 'Paid', cell: (row) => <When value={row.paidAt} /> },
    ...(canWrite
      ? [
          {
            header: 'Actions',
            headClassName: 'text-right',
            className: 'text-right',
            cell: (row) => {
              const actions = actionsFor(row, act);
              return actions.length ? (
                <div className="flex justify-end">
                  <RowActions label={row.publicId} actions={actions} />
                </div>
              ) : null;
            }
          }
        ]
      : [])
  ];

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Payments & Payouts' }, { label: 'Partner Payouts' }]}
        title="Partner Payouts"
        description="What partners are owed for completed online bookings, after the platform's commission."
      />

      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:grid xl:grid-cols-4 xl:overflow-visible xl:px-0 xl:pb-0">
        {TILES.map((tile) => (
          <MoneyTile
            key={tile.status}
            icon={tile.icon}
            tone={tile.tone}
            label={tile.label}
            value={stats && formatAmount(stats.netByStatus[tile.status] ?? 0)}
          />
        ))}
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <div className="mb-4 flex justify-end">
          <div className="w-56">
            <Select
              ariaLabel="Payout status"
              value={status}
              onChange={setStatus}
              options={[
                { value: '', label: 'All Status' },
                ...(values.PAYOUT_STATUS ?? []).map((value) => ({ value: value.valueCode, label: value.displayName }))
              ]}
            />
          </div>
        </div>
        <PagedTable
          path={`/api/payouts${status ? `?payoutStatus=${status}` : ''}`}
          columns={columns}
          rowKey={(row) => row.payoutId}
          reloadKey={reloadKey}
          minWidth="64rem"
          emptyText="No payouts yet: they appear when paid online bookings are completed."
        />
      </section>

      {adjusting && (
        <FormModal
          title={`Adjust ${adjusting.publicId}`}
          fields={[
            {
              key: 'adjustmentsAmount',
              label: 'Adjustment (₹)',
              type: 'text',
              required: true,
              hint: 'A bonus is positive, a deduction negative. It replaces the current adjustment.',
              validate: (value) => (/^-?\d+(\.\d{1,2})?$/.test(value.trim()) ? null : 'Enter an amount, e.g. 50 or -25')
            }
          ]}
          initial={{ adjustmentsAmount: String(adjusting.adjustmentsAmount ?? 0) }}
          submitLabel="Save Adjustment"
          onSubmit={saveAdjustment}
          onClose={() => setAdjusting(null)}
        />
      )}
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
