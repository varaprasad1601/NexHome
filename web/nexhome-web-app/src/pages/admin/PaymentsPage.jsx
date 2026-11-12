import { Banknote, Clock, HandCoins, Hourglass, IndianRupee, Percent, RotateCcw, Search, Undo2 } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

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
import { cardClass, linkClass, resetButtonClass, searchInputClass } from '../../components/admin/system/systemStyles';
import { useAdminAuth } from '../../context/adminAuth';
import { formatAmount } from '../../data/bookingOptions';
import { PAYMENT_LOOKUPS, paymentModeLabel } from '../../data/paymentOptions';
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
  // The tab is in the address (?tab=orders), so Back from an order's page returns to Payment Orders
  const [params, setParams] = useSearchParams();
  const tab = ['payments', 'cash', 'orders'].includes(params.get('tab')) ? params.get('tab') : 'payments';
  const setTab = (key) => setParams(key === 'payments' ? {} : { tab: key }, { replace: true });
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
  const statusType = tab === 'cash' ? 'CASH_COLLECTION_STATUS' : tab === 'orders' ? 'PAYMENT_ORDER_STATUS' : 'PAYMENT_STATUS';
  // Each tab's search: what the backend matches it against
  const searchHint = {
    payments: 'Search by booking ID, order ID or payment reference...',
    cash: 'Search by booking ID, customer or partner (name or ID)...',
    orders: 'Search by order ID, booking ID or customer (name or ID)...'
  }[tab];
  const statusOptions = [
    { value: '', label: 'All Status' },
    ...(values[statusType] ?? []).map((value) => ({ value: value.valueCode, label: value.displayName }))
  ];
  const query = new URLSearchParams();
  if (status) query.set('status', status);
  if (search) query.set('search', search);

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
    {
      header: 'Paid Via',
      cell: (row) => (
        <span className="block whitespace-nowrap">
          <span className="font-bold">{paymentModeLabel(row)}</span>
          <span className="block text-xs text-[var(--muted)]">{row.provider === 'CASHFREE' ? 'Cashfree' : row.provider === 'MANUAL' ? 'By staff' : row.provider}</span>
        </span>
      )
    },
    {
      // What the gateway kept (V68): reported by webhook or the hourly check, after the payment
      header: 'Gateway Charges',
      cell: (row) =>
        row.gatewayChargesAmount != null ? (
          formatAmount(row.gatewayChargesAmount)
        ) : (
          <span className="text-[var(--muted)]">{row.provider === 'MANUAL' || row.paymentMethod !== 'ONLINE' ? '—' : 'Pending'}</span>
        )
    },
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
    {
      header: 'Order',
      cell: (row) => (
        <Link to={`/admin/payments/customers/orders/${row.paymentOrderId}`} className={`${linkClass} font-black`}>
          {row.publicId}
        </Link>
      )
    },
    {
      header: 'Booking',
      cell: (row) =>
        row.bookings?.length ? (
          <span className="flex flex-col">
            {row.bookings.map((booking) => (
              <BookingLink key={booking.bookingId} id={booking.bookingId} publicId={booking.publicId} />
            ))}
          </span>
        ) : (
          <span className="text-[var(--muted)]">—</span>
        )
    },
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
          note={stats && `${stats.capturedCount} ${stats.capturedCount === 1 ? 'payment' : 'payments'}`}
        />
        <MoneyTile
          icon={Clock}
          tone="bg-[#FFF1E6] text-[#C2570C]"
          label="Pending Payments"
          value={stats && formatAmount(stats.pendingPaymentAmount ?? 0)}
          note={stats && `${stats.pendingPaymentCount ?? 0} ${stats.pendingPaymentCount === 1 ? 'booking' : 'bookings'} not paid yet`}
        />
        <MoneyTile
          icon={Hourglass}
          tone="bg-[#FFF7DE] text-[#B7860B]"
          label="Awaiting Payment"
          value={stats && formatAmount(stats.ordersAwaitingAmount ?? 0)}
          note={stats && `${stats.ordersAwaitingPayment} open ${stats.ordersAwaitingPayment === 1 ? 'order' : 'orders'}`}
        />
        <MoneyTile
          icon={HandCoins}
          tone="bg-[#E6F7F4] text-[#0F8A75]"
          label="Cash Collected"
          value={stats && formatAmount(stats.cashCollectedAmount ?? 0)}
          note={stats && `${stats.cashCollectedCount ?? 0} ${stats.cashCollectedCount === 1 ? 'booking' : 'bookings'}`}
        />
        <MoneyTile
          icon={Banknote}
          tone="bg-[#EDF6FD] text-[var(--blue)]"
          label="Cash Due"
          value={stats && formatAmount(stats.cashDueAmount)}
          note={stats && `${stats.cashDueCount} ${stats.cashDueCount === 1 ? 'booking' : 'bookings'}`}
        />
        <MoneyTile
          icon={Percent}
          tone="bg-[#FDECEC] text-[#C53030]"
          label="Gateway Charges"
          value={stats && formatAmount(stats.gatewayChargesAmount ?? 0)}
        />
        <MoneyTile
          icon={Undo2}
          tone="bg-[#E8EEF6] text-[var(--navy)]"
          label="Refunded"
          value={stats && formatAmount(stats.refundedAmount)}
          note={stats && `${stats.refundCount} ${stats.refundCount === 1 ? 'refund' : 'refunds'}`}
        />
      </div>

      <section className={`mt-5 ${cardClass}`}>
        <MoneyTabs tabs={TABS} active={tab} onChange={changeTab} />
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_14rem_auto]">
          <label className="relative">
            <span className="sr-only">Search</span>
            <Search
              size={16}
              className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]"
            />
            <input
              type="search"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              placeholder={searchHint}
              className={searchInputClass}
            />
          </label>
          <Select ariaLabel="Status" value={status} onChange={setStatus} options={statusOptions} />
          <button type="button" className={resetButtonClass} onClick={() => changeTab(tab)}>
            <RotateCcw size={15} />
            Reset
          </button>
        </div>
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
            emptyText={
              search || status
                ? 'No cash collections match your search or filters.'
                : 'No cash collections yet: they appear when cash bookings are completed.'
            }
          />
        )}
        {tab === 'orders' && (
          <PagedTable
            path={`/api/payments/orders?${query}`}
            columns={orderColumns}
            rowKey={(row) => row.paymentOrderId}
            reloadKey={reloadKey}
            emptyText={search || status ? 'No payment orders match your search or filters.' : 'No payment orders yet.'}
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
