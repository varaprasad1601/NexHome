import { CalendarCheck, HandCoins, Plus, Search, Users, Wallet } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import FormModal from '../../components/admin/common/FormModal';
import Toast from '../../components/admin/common/Toast';
import PagedTable from '../../components/admin/payment/PagedTable';
import RecordPayoutModal from '../../components/admin/payment/RecordPayoutModal';
import { MoneyTabs, MoneyTile, PartnerLink, When } from '../../components/admin/payment/moneyParts';
import { payoutModeLabel, paidOnLabel } from '../../components/admin/payment/payoutParts';
import RowActions from '../../components/admin/system/RowActions';
import { cardClass, primaryButtonClass, searchInputClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { formatAmount } from '../../data/bookingOptions';
import useDebounced from '../../hooks/useDebounced';
import useLookupLabels from '../../hooks/useLookupLabels';

const TABS = [
  { key: 'dues', label: 'Due to Partners' },
  { key: 'payouts', label: 'Payouts Made' },
  { key: 'deductions', label: 'Deductions' }
];
const LOOKUPS = ['PAYOUT_MODE', 'DEDUCTION_REASON'];

/**
 * Payments & Payouts > Partner Payouts (/admin/payments/payouts, PAYOUT_MANAGE read; V62). Partners hand the cash of
 * cash jobs to NexHome, so NexHome owes each partner every completed job (cash and online) less its commission, and
 * pays them outside the app. Tiles: due to partners, partners owed, paid this month, paid in all
 * (GET /api/payouts/manual/stats). **Due to Partners** (GET /api/payouts/dues): earned, paid, due, with write a Pay
 * button; **Payouts Made** (GET /api/payouts/manual): every recorded payment (with the deduction recorded with it), with
 * write ⋮ Cancel Payout (a reason); **Deductions** (V63, GET /api/payouts/deductions): what NexHome kept (reason, note,
 * date, the payout it came with), with write ⋮ Cancel Deduction. With write, **Record Payout / Deduction** opens the dialog
 * (RecordPayoutModal: the type first, Payout or Deduction, then its fields).
 */
export default function PayoutsPage() {
  const { authRequest, can } = useAdminAuth();
  const canWrite = can('PAYOUT_MANAGE', 'WRITE');
  const { label } = useLookupLabels(LOOKUPS);
  const [tab, setTab] = useState('dues');
  const [stats, setStats] = useState(null);
  const [searchText, setSearchText] = useState('');
  const search = useDebounced(searchText.trim());
  const [reloadKey, setReloadKey] = useState(0);
  const [recording, setRecording] = useState(null);
  const [cancelling, setCancelling] = useState(null);
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);
  const showToast = useCallback((message, tone = 'success') => setToast({ message, tone, id: Date.now() }), []);

  useEffect(() => {
    document.title = 'Partner Payouts | NexHome Admin';
    authRequest('/api/payouts/manual/stats')
      .then(setStats)
      .catch(() => setStats(null));
  }, [authRequest, reloadKey]);

  const saved = (message) => {
    setRecording(null);
    showToast(message);
    setReloadKey((key) => key + 1);
  };

  const duesPath = `/api/payouts/dues${search ? `?search=${encodeURIComponent(search)}` : ''}`;
  const dueColumns = [
    { header: 'Partner', cell: (row) => <PartnerLink id={row.partnerProfileId} name={row.partnerName ?? row.publicId} /> },
    { header: 'Partner ID', cell: (row) => <span className="font-bold text-[var(--muted)]">{row.publicId}</span> },
    { header: 'Mobile', cell: (row) => <span className="whitespace-nowrap">{row.mobileNumber}</span> },
    { header: 'Jobs', cell: (row) => row.jobs },
    { header: 'Earned', cell: (row) => formatAmount(row.totalEarned) },
    { header: 'Paid', cell: (row) => <span className="text-[var(--muted)]">{formatAmount(row.paidTotal)}</span> },
    {
      header: 'Deducted',
      cell: (row) =>
        Number(row.deductedTotal) > 0 ? (
          <span className="font-bold text-[#C53030]">− {formatAmount(row.deductedTotal)}</span>
        ) : (
          <span className="text-[var(--muted)]">—</span>
        )
    },
    {
      header: 'Due',
      cell: (row) => (
        <span className={`font-black ${Number(row.balance) > 0 ? 'text-[var(--green-2)]' : 'text-[var(--muted)]'}`}>
          {formatAmount(row.balance)}
        </span>
      )
    },
    ...(canWrite
      ? [
          {
            header: 'Actions',
            headClassName: 'text-right',
            className: 'text-right',
            cell: (row) =>
              Number(row.balance) > 0 ? (
                <button
                  type="button"
                  onClick={() => setRecording(row)}
                  className="rounded-lg bg-[var(--green)] px-3 py-1.5 text-[0.8125rem] font-bold text-white hover:opacity-90"
                >
                  Pay
                </button>
              ) : null
          }
        ]
      : [])
  ];

  const payoutColumns = [
    { header: 'Payout ID', cell: (row) => <span className="font-black">{row.publicId}</span> },
    { header: 'Partner', cell: (row) => <PartnerLink id={row.partnerProfileId} name={row.partnerName ?? row.partnerPublicId} /> },
    {
      header: 'Amount',
      cell: (row) => (
        <span className={`font-black ${row.payoutStatus === 'CANCELLED' ? 'text-[var(--muted)] line-through' : 'text-[var(--green-2)]'}`}>
          {formatAmount(row.amount)}
        </span>
      )
    },
    {
      header: 'Paid Through',
      cell: (row) => (
        <span className="block whitespace-nowrap">
          {payoutModeLabel(label, row.paymentMode)}
          {row.deduction && row.deduction.status === 'RECORDED' && (
            <span className="block text-xs font-bold text-[#C53030]">
              − {formatAmount(row.deduction.amount)} {label('DEDUCTION_REASON', row.deduction.deductionReason)}
            </span>
          )}
        </span>
      )
    },
    {
      header: 'Transaction ID',
      cell: (row) => <span className="font-mono text-[0.8125rem]">{row.transactionId ?? '—'}</span>
    },
    { header: 'Paid On', cell: (row) => <span className="whitespace-nowrap font-bold">{paidOnLabel(row.paidOn)}</span> },
    {
      header: 'Recorded',
      cell: (row) => (
        <span className="block">
          <When value={row.creationDate} />
          {row.createdByName && <span className="block text-xs text-[var(--muted)]">by {row.createdByName}</span>}
        </span>
      )
    },
    {
      header: 'Status',
      cell: (row) =>
        row.payoutStatus === 'CANCELLED' ? (
          <span title={row.cancellationReason} className="whitespace-nowrap rounded-full bg-[#FDECEC] px-2.5 py-1 text-[0.75rem] font-bold text-[#C53030]">
            Cancelled
          </span>
        ) : (
          <span className="whitespace-nowrap rounded-full bg-[#E9F6EF] px-2.5 py-1 text-[0.75rem] font-bold text-[var(--green-2)]">Paid</span>
        )
    },
    ...(canWrite
      ? [
          {
            header: 'Actions',
            headClassName: 'text-right',
            className: 'text-right',
            cell: (row) =>
              row.payoutStatus === 'RECORDED' ? (
                <div className="flex justify-end">
                  <RowActions
                    label={row.publicId}
                    actions={[{ label: 'Cancel Payout', danger: true, onClick: () => setCancelling(row) }]}
                  />
                </div>
              ) : null
          }
        ]
      : [])
  ];

  const deductionColumns = [
    { header: 'Deduction ID', cell: (row) => <span className="font-black">{row.publicId}</span> },
    { header: 'Partner', cell: (row) => <PartnerLink id={row.partnerProfileId} name={row.partnerName ?? row.partnerPublicId} /> },
    {
      header: 'Amount',
      cell: (row) => (
        <span className={`font-black ${row.status === 'CANCELLED' ? 'text-[var(--muted)] line-through' : 'text-[#C53030]'}`}>
          − {formatAmount(row.amount)}
        </span>
      )
    },
    { header: 'Reason', cell: (row) => <span className="whitespace-nowrap font-bold">{label('DEDUCTION_REASON', row.deductionReason)}</span> },
    {
      header: 'Note',
      className: 'max-w-[16rem]',
      cell: (row) => (
        <span className="block truncate text-[0.8125rem]" title={row.notes ?? ''}>
          {row.notes ?? '—'}
        </span>
      )
    },
    { header: 'Date', cell: (row) => <span className="whitespace-nowrap font-bold">{paidOnLabel(row.deductedOn)}</span> },
    {
      header: 'With Payout',
      cell: (row) => <span className="whitespace-nowrap text-[0.8125rem] text-[var(--muted)]">{row.payoutPublicId ?? '—'}</span>
    },
    {
      header: 'Recorded',
      cell: (row) => (
        <span className="block">
          <When value={row.creationDate} />
          {row.createdByName && <span className="block text-xs text-[var(--muted)]">by {row.createdByName}</span>}
        </span>
      )
    },
    {
      header: 'Status',
      cell: (row) =>
        row.status === 'CANCELLED' ? (
          <span title={row.cancellationReason} className="whitespace-nowrap rounded-full bg-[#F1F4F8] px-2.5 py-1 text-[0.75rem] font-bold text-[var(--muted)]">
            Cancelled
          </span>
        ) : (
          <span className="whitespace-nowrap rounded-full bg-[#FDECEC] px-2.5 py-1 text-[0.75rem] font-bold text-[#C53030]">Deducted</span>
        )
    },
    ...(canWrite
      ? [
          {
            header: 'Actions',
            headClassName: 'text-right',
            className: 'text-right',
            cell: (row) =>
              row.status === 'RECORDED' ? (
                <div className="flex justify-end">
                  <RowActions
                    label={row.publicId}
                    actions={[{ label: 'Cancel Deduction', danger: true, onClick: () => setCancelling({ ...row, kind: 'deduction' }) }]}
                  />
                </div>
              ) : null
          }
        ]
      : [])
  ];

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Payments & Payouts' }, { label: 'Partner Payouts' }]}
        title="Partner Payouts"
        description="What NexHome owes partners for completed jobs (cash and online, after commission), and the payments made to them."
        actions={
          canWrite ? (
            <button type="button" onClick={() => setRecording('new')} className={primaryButtonClass}>
              <Plus size={16} />
              Record Payout / Deduction
            </button>
          ) : null
        }
      />

      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:grid xl:grid-cols-4 xl:overflow-visible xl:px-0 xl:pb-0">
        <MoneyTile icon={Wallet} tone="bg-[#FFF7DE] text-[#B7860B]" label="Due to Partners" value={stats && formatAmount(stats.totalDue)} />
        <MoneyTile icon={Users} tone="bg-[#EDF6FD] text-[var(--blue)]" label="Partners Owed" value={stats && String(stats.partnersDue)} />
        <MoneyTile
          icon={CalendarCheck}
          tone="bg-[#E9F6EF] text-[var(--green-2)]"
          label="Paid This Month"
          value={stats && formatAmount(stats.paidThisMonth)}
          note={stats ? `${stats.payoutsThisMonth} payout${stats.payoutsThisMonth === 1 ? '' : 's'}` : undefined}
        />
        <MoneyTile
          icon={HandCoins}
          tone="bg-[#E8EEF6] text-[var(--navy)]"
          label="Paid in All"
          value={stats && formatAmount(stats.paidTotal)}
          note={stats ? `${stats.payoutCount} payout${stats.payoutCount === 1 ? '' : 's'}` : undefined}
        />
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <MoneyTabs tabs={TABS} active={tab} onChange={setTab} />
          {tab === 'dues' && (
            <div className="flex flex-wrap items-center gap-3">
              <label className="relative w-[16rem]">
                <span className="sr-only">Search partners</span>
                <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]" />
                <input
                  type="search"
                  value={searchText}
                  onChange={(e) => setSearchText(e.target.value)}
                  placeholder="Name, partner ID or mobile..."
                  className={searchInputClass}
                />
              </label>
            </div>
          )}
        </div>
        {tab === 'dues' ? (
          <PagedTable
            path={duesPath}
            columns={dueColumns}
            rowKey={(row) => row.partnerProfileId}
            reloadKey={reloadKey}
            minWidth="60rem"
            emptyText={search ? 'No partner matches the search.' : 'No partner has completed a job yet.'}
          />
        ) : tab === 'payouts' ? (
          <PagedTable
            path="/api/payouts/manual"
            columns={payoutColumns}
            rowKey={(row) => row.payoutId}
            reloadKey={reloadKey}
            minWidth="64rem"
            emptyText="No payouts recorded yet."
          />
        ) : (
          <PagedTable
            path="/api/payouts/deductions"
            columns={deductionColumns}
            rowKey={(row) => row.deductionId}
            reloadKey={reloadKey}
            minWidth="68rem"
            emptyText="No deductions recorded yet."
          />
        )}
      </section>

      {recording && (
        <RecordPayoutModal
          authRequest={authRequest}
          partner={recording === 'new' ? null : recording}
          onClose={() => setRecording(null)}
          onSaved={saved}
        />
      )}
      {cancelling && (
        <FormModal
          title={`Cancel ${cancelling.publicId}`}
          fields={[
            {
              key: 'reason',
              label: 'Reason',
              type: 'textarea',
              required: true,
              maxLength: 200,
              hint:
                cancelling.kind === 'deduction'
                  ? 'Why the deduction is cancelled. It then no longer counts, so NexHome owes the partner that amount again.'
                  : 'Why the payout is cancelled, e.g. recorded twice. It then no longer counts as paid.'
            }
          ]}
          initial={{ reason: '' }}
          submitLabel={cancelling.kind === 'deduction' ? 'Cancel Deduction' : 'Cancel Payout'}
          onSubmit={async (formValues) => {
            if (cancelling.kind === 'deduction') {
              await authRequest('/api/payouts/deductions/cancel', {
                method: 'PUT',
                body: { deductionId: cancelling.deductionId, reason: formValues.reason.trim() }
              });
            } else {
              await authRequest('/api/payouts/manual/cancel', {
                method: 'PUT',
                body: { payoutId: cancelling.payoutId, reason: formValues.reason.trim() }
              });
            }
            setCancelling(null);
            showToast(`${cancelling.kind === 'deduction' ? 'Deduction' : 'Payout'} ${cancelling.publicId} cancelled.`);
            setReloadKey((key) => key + 1);
          }}
          onClose={() => setCancelling(null)}
        />
      )}
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
