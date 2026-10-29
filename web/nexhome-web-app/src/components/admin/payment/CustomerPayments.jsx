import PagedTable from './PagedTable';
import { BookingLink, MoneyBadge, When } from './moneyParts';
import { formatAmount } from '../../../data/bookingOptions';
import { PAYMENT_LOOKUPS } from '../../../data/paymentOptions';
import useLookupLabels from '../../../hooks/useLookupLabels';

/** The customer profile's Payments tab: the customer's payments (GET /api/payments?customerProfileId=). */
export default function CustomerPayments({ customerProfileId }) {
  const { label } = useLookupLabels(PAYMENT_LOOKUPS);
  return (
    <PagedTable
      path={`/api/payments?customerProfileId=${customerProfileId}`}
      rowKey={(row) => row.paymentId}
      emptyText="This customer has not paid online yet (cash goes to the partner)."
      columns={[
        { header: 'Booking', cell: (row) => <BookingLink id={row.bookingId} publicId={row.bookingPublicId} /> },
        {
          header: 'Reference',
          cell: (row) => <span className="text-[var(--muted)]">{row.providerPaymentId ?? '—'}</span>
        },
        { header: 'Amount', cell: (row) => <span className="font-black">{formatAmount(row.amount)}</span> },
        {
          header: 'Status',
          cell: (row) => <MoneyBadge status={row.status} label={label('PAYMENT_STATUS', row.status)} />
        },
        { header: 'Paid On', cell: (row) => <When value={row.capturedAt} /> },
        {
          header: 'Refunded',
          cell: (row) =>
            Number(row.refundedAmount) > 0 ? (
              formatAmount(row.refundedAmount)
            ) : (
              <span className="text-[var(--muted)]">—</span>
            )
        }
      ]}
    />
  );
}
