import { useEffect, useState } from 'react';

import AdminPageHeader from '../../components/admin/AdminPageHeader';
import Select from '../../components/admin/common/Select';
import PagedTable from '../../components/admin/payment/PagedTable';
import { BookingLink, MoneyBadge, When } from '../../components/admin/payment/moneyParts';
import { cardClass } from '../../components/admin/system/systemStyles';
import { formatAmount } from '../../data/bookingOptions';
import { PAYMENT_LOOKUPS } from '../../data/paymentOptions';
import useLookupLabels from '../../hooks/useLookupLabels';

/**
 * Payments & Payouts > Refunds (/admin/payments/refunds, PAYMENT_MANAGE read): every refund, newest first, with a
 * status filter. Refunds are made from a booking's Payment card (or happen when a paid booking is cancelled).
 */
export default function RefundsPage() {
  const { values, label } = useLookupLabels(PAYMENT_LOOKUPS);
  const [status, setStatus] = useState('');

  useEffect(() => {
    document.title = 'Refunds | NexHome Admin';
  }, []);

  const columns = [
    { header: 'Booking', cell: (row) => <BookingLink id={row.bookingId} publicId={row.bookingPublicId} /> },
    { header: 'Customer', cell: (row) => row.customerName ?? <span className="text-[var(--muted)]">—</span> },
    { header: 'Amount', cell: (row) => <span className="font-black">{formatAmount(row.amount)}</span> },
    {
      header: 'Reason',
      className: 'max-w-0',
      cell: (row) => (
        <span className="block max-w-[18rem] truncate" title={row.reason}>
          {row.reason ?? '—'}
        </span>
      )
    },
    { header: 'Status', cell: (row) => <MoneyBadge status={row.status} label={label('REFUND_STATUS', row.status)} /> },
    { header: 'Reference', cell: (row) => <span className="text-[var(--muted)]">{row.providerRefundId ?? '—'}</span> },
    { header: 'Completed', cell: (row) => <When value={row.completedAt} /> }
  ];

  return (
    <div className="mx-auto max-w-[92.5rem]">
      <AdminPageHeader
        crumbs={[{ label: 'Payments & Payouts' }, { label: 'Refunds' }]}
        title="Refunds"
        description="Money returned to customers, from refunds made on a booking and cancelled paid bookings."
      />
      <section className={cardClass}>
        <div className="mb-4 flex justify-end">
          <div className="w-56">
            <Select
              ariaLabel="Refund status"
              value={status}
              onChange={setStatus}
              options={[
                { value: '', label: 'All Status' },
                ...(values.REFUND_STATUS ?? []).map((value) => ({ value: value.valueCode, label: value.displayName }))
              ]}
            />
          </div>
        </div>
        <PagedTable
          path={`/api/payments/refunds${status ? `?status=${status}` : ''}`}
          columns={columns}
          rowKey={(row) => row.refundId}
          emptyText="No refunds yet."
        />
      </section>
    </div>
  );
}
