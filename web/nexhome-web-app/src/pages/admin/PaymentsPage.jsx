import { Banknote, Hourglass, IndianRupee, RotateCcw, Search, Undo2 } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import FormModal from '../../components/admin/common/FormModal';
import Select from '../../components/admin/common/Select';
import Toast from '../../components/admin/common/Toast';
import PagedTable from '../../components/admin/payment/PagedTable';
import {
  BookingLink,
  CustomerLink,
  MoneyBadge,
  MoneyTabs,
  MoneyTile,
  PartnerLink,
  When
} from '../../components/admin/payment/moneyParts';
import RowActions from '../../components/admin/system/RowActions';
import { cardClass, resetButtonClass, searchInputClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { formatAmount } from '../../data/bookingOptions';
import { PAYMENT_LOOKUPS } from '../../data/paymentOptions';
import useDebounced from '../../hooks/useDebounced';
import useLookupLabels from '../../hooks/useLookupLabels';

const TABS = [
  { key: 'payments', label: 'Payments' },
  { key: 'cash', label: 'Cash Collections' },
  { key: 'orders', label: 'Payment Orders' }
];
/** What staff can do with cash, by its status (the backend's CASH_TRANSITIONS). */
const CASH_ACTIONS = {
  DUE: [
    { status: 'COLLECTED', label: 'Mark Collected' },
    { status: 'DISPUTED', label: 'Mark Disputed', danger: true },
    { status: 'WAIVED', label: 'Waive', danger: true }
  ],
  DISPUTED: [
    { status: 'COLLECTED', label: 'Mark Collected' },
    { status: 'WAIVED', label: 'Waive', danger: true }
  ]
};

/**
 * Payments & Payouts > Customer Payments (/admin/payments/customers, PAYMENT_MANAGE read): money in. Tiles from
 * GET /api/payments/stats; tabs Payments (captured, failed and refunded payments: status filter and search),
 * Cash Collections (cash due from completed cash bookings; with write Mark Collected, Disputed or Waive, with notes)
 * and Payment Orders (the gateway orders, MANUAL until a provider is connected).
 */
export default function PaymentsPage() {
  const { authRequest, can } = useAdminAuth();
  const canWrite = can('PAYMENT_MANAGE', 'WRITE');
  const { values, label } = useLookupLabels(PAYMENT_LOOKUPS);
  const [tab, setTab] = useState('payments');
  const [status, setStatus] = useState('');
  const [searchText, setSearchText] = useState('');
  const search = useDebounced(searchText.trim());
  const [stats, setStats] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [cashAction, setCashAction] = useState(null); // { cash, status, label }
  const [toast, setToast] = useState(null);
  const closeToast = useCallback(() => setToast(null), []);

  useEffect(() => {
    document.title = 'Customer Payments | NexHome Admin';
    authRequest('/api/payments/stats')
      .then(setStats)
      .catch(() => setStats(null));
  }, [authRequest, reloadKey]);

  const changeTab = (key) => {
    setTab(key);
    setStatus('');
    setSearchText('');
  };
  const statusType = tab === 'cash' ? 'CASH_COLLECTION_STATUS' : 'PAYMENT_STATUS';
  const statusOptions = [
    { value: '', label: 'All Status' },
    ...(values[statusType] ?? []).map((value) => ({ value: value.valueCode, label: value.displayName }))
  ];
  const query = new URLSearchParams();
  if (status) query.set('status', status);
  if (search && tab === 'payments') query.set('search', search);

  const saveCash = async (formValues) => {
    await authRequest('/api/payments/cash-collections/update', {
      method: 'PUT',
      body: {
        cashCollectionId: cashAction.cash.cashCollectionId,
        status: cashAction.status,
        notes: formValues.notes.trim() || null
      }
    });
    setCashAction(null);
    setToast({ message: `Cash for ${cashAction.cash.bookingPublicId} updated.`, tone: 'success', id: Date.now() });
    setReloadKey((key) => key + 1);
  };

  const paymentColumns = [
    { header: 'Reference ID', cell: (row) => <span className="font-bold">{row.providerPaymentId ?? '—'}</span> },
    { header: 'Booking', cell: (row) => <BookingLink id={row.bookingId} publicId={row.bookingPublicId} /> },
    { header: 'Customer', cell: (row) => <CustomerLink id={row.customerProfileId} name={row.customerName} /> },
    { header: 'Total Amount', cell: (row) => formatAmount(row.bookingTotalAmount) },
    { header: 'Amount Paid', cell: (row) => <span className="font-black">{formatAmount(row.amount)}</span> },
    { header: 'Status', cell: (row) => <MoneyBadge status={row.status} label={label('PAYMENT_STATUS', row.status)} /> },
    {
      header: 'Refunded',
      cell: (row) =>
        Number(row.refundedAmount) > 0 ? (
          formatAmount(row.refundedAmount)
        ) : (
          <span className="text-[var(--muted)]">—</span>
        )
    },
    { header: 'Captured', cell: (row) => <When value={row.capturedAt} /> }
  ];
  const cashColumns = [
    { header: 'Booking', cell: (row) => <BookingLink id={row.bookingId} publicId={row.bookingPublicId} /> },
    { header: 'Customer', cell: (row) => <CustomerLink id={row.customerProfileId} name={row.customerName} /> },
    { header: 'Partner', cell: (row) => <PartnerLink id={row.partnerProfileId} name={row.partnerName} /> },
    { header: 'Amount', cell: (row) => <span className="font-black">{formatAmount(row.amount)}</span> },
    {
      header: 'Status',
      cell: (row) => <MoneyBadge status={row.status} label={label('CASH_COLLECTION_STATUS', row.status)} />
    },
    { header: 'Collected', cell: (row) => <When value={row.confirmedAt} /> },
    {
      header: 'Customer Confirmed',
      cell: (row) =>
        row.customerAcknowledgedAt ? (
          <When value={row.customerAcknowledgedAt} />
        ) : (
          <span className="text-[var(--muted)]">No</span>
        )
    },
    ...(canWrite
      ? [
          {
            header: 'Actions',
            headClassName: 'text-right',
            className: 'text-right',
            cell: (row) =>
              CASH_ACTIONS[row.status] ? (
                <div className="flex justify-end">
                  <RowActions
                    label={row.bookingPublicId}
                    actions={CASH_ACTIONS[row.status].map((action) => ({
                      label: action.label,
                      danger: action.danger,
                      onClick: () => setCashAction({ cash: row, ...action })
                    }))}
                  />
                </div>
              ) : null
          }
        ]
      : [])
  ];
  const orderColumns = [
    { header: 'Order', cell: (row) => <span className="font-black">{row.publicId}</span> },
    { header: 'Customer', cell: (row) => <CustomerLink id={row.customerProfileId} name={row.customerName} /> },
    { header: 'Provider', cell: (row) => row.provider },
    {
      header: 'Provider Order',
      cell: (row) => <span className="text-[var(--muted)]">{row.providerOrderId ?? '—'}</span>
    },
    { header: 'Amount', cell: (row) => <span className="font-black">{formatAmount(row.amount)}</span> },
    {
      header: 'Status',
      cell: (row) => <MoneyBadge status={row.paymentStatus} label={label('PAYMENT_ORDER_STATUS', row.paymentStatus)} />
    },
    { header: 'Created', cell: (row) => <When value={row.creationDate} /> }
  ];

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Payments & Payouts' }, { label: 'Customer Payments' }]}
        title="Customer Payments"
        description="Money in: online payments, cash collected by partners, and the payment orders behind them."
      />

      <div className="nh-scroll-hidden -mx-4 flex gap-4 overflow-x-auto px-4 pb-1 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8 xl:mx-0 xl:grid xl:grid-cols-4 xl:overflow-visible xl:px-0 xl:pb-0">
        <MoneyTile
          icon={IndianRupee}
          tone="bg-[#E9F6EF] text-[var(--green-2)]"
          label="Captured"
          value={stats && formatAmount(stats.capturedAmount)}
        />
        <MoneyTile
          icon={Undo2}
          tone="bg-[#E8EEF6] text-[var(--navy)]"
          label="Refunded"
          value={stats && formatAmount(stats.refundedAmount)}
        />
        <MoneyTile
          icon={Hourglass}
          tone="bg-[#FFF7DE] text-[#B7860B]"
          label="Awaiting Payment"
          value={stats && String(stats.ordersAwaitingPayment)}
        />
        <MoneyTile
          icon={Banknote}
          tone="bg-[#EDF6FD] text-[var(--blue)]"
          label="Cash Due"
          value={stats && formatAmount(stats.cashDueAmount)}
        />
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <MoneyTabs tabs={TABS} active={tab} onChange={changeTab} />
        {tab !== 'orders' && (
          <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_14rem_auto]">
            {tab === 'payments' ? (
              <label className="relative">
                <span className="sr-only">Search payments</span>
                <Search
                  size={16}
                  className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]"
                />
                <input
                  type="search"
                  value={searchText}
                  onChange={(e) => setSearchText(e.target.value)}
                  placeholder="Search by booking ID, order ID or payment reference..."
                  className={searchInputClass}
                />
              </label>
            ) : (
              <span />
            )}
            <Select ariaLabel="Status" value={status} onChange={setStatus} options={statusOptions} />
            <button type="button" className={resetButtonClass} onClick={() => changeTab(tab)}>
              <RotateCcw size={15} />
              Reset
            </button>
          </div>
        )}
        {tab === 'payments' && (
          <PagedTable
            path={`/api/payments?${query}`}
            columns={paymentColumns}
            rowKey={(row) => row.paymentId}
            reloadKey={reloadKey}
            emptyText="No payments match your search or filters."
          />
        )}
        {tab === 'cash' && (
          <PagedTable
            path={`/api/payments/cash-collections?${query}`}
            columns={cashColumns}
            rowKey={(row) => row.cashCollectionId}
            reloadKey={reloadKey}
            emptyText="No cash collections yet: they appear when cash bookings are completed."
          />
        )}
        {tab === 'orders' && (
          <PagedTable
            path="/api/payments/orders"
            columns={orderColumns}
            rowKey={(row) => row.paymentOrderId}
            reloadKey={reloadKey}
            emptyText="No payment orders yet."
          />
        )}
      </section>

      {cashAction && (
        <FormModal
          title={cashAction.label}
          fields={[{ key: 'notes', label: 'Notes', type: 'textarea', rows: 3, maxLength: 2000, wide: true }]}
          initial={{ notes: cashAction.cash.notes ?? '' }}
          submitLabel={cashAction.label}
          onSubmit={saveCash}
          onClose={() => setCashAction(null)}
        />
      )}
      <Toast toast={toast} onClose={closeToast} />
    </div>
  );
}
