import { EyeOff, Hourglass, Send } from 'lucide-react';

import PagedTable from '../payment/PagedTable';
import { BookingLink, CustomerLink, PartnerLink, When } from '../payment/moneyParts';
import RowActions from '../system/RowActions';
import Stars from './Stars';
import { useAdminAuth } from '../../../context/adminAuth';
import { serviceNameOf } from '../../../data/bookingOptions';
import { supportBadgeClass } from '../../../data/supportOptions';
import useLookupLabels from '../../../hooks/useLookupLabels';

const REVIEW_LOOKUP = ['REVIEW_STATUS'];

/** The moderation actions a review offers: every status but its own. */
const STATUS_ACTIONS = [
  { status: 'PUBLISHED', label: 'Publish', icon: Send },
  { status: 'HIDDEN', label: 'Hide', icon: EyeOff },
  { status: 'UNDER_REVIEW', label: 'Hold for Review', icon: Hourglass }
];

/**
 * A server-paged table of reviews (GET /api/reviews with `query`, e.g. "?partnerProfileId=3"): booking, customer,
 * partner, service, both ratings, the text, status and date; `hide` drops the customer or partner column (on their
 * own profile). With REVIEW_MANAGE write each row has a ⋮ menu to publish, hide or hold it, through `onSetStatus`.
 */
export default function ReviewsTable({ query = '', label: labelProp, hide = [], reloadKey = 0, onSetStatus }) {
  const { can } = useAdminAuth();
  const { label: ownLabel } = useLookupLabels(REVIEW_LOOKUP);
  const label = labelProp ?? ownLabel;
  const canWrite = can('REVIEW_MANAGE', 'WRITE') && onSetStatus;
  const columns = [
    { header: 'Booking', cell: (row) => <BookingLink id={row.bookingId} publicId={row.bookingPublicId} /> },
    !hide.includes('customer') && {
      header: 'Customer',
      cell: (row) => <CustomerLink id={row.customerProfileId} name={row.customerName} />
    },
    !hide.includes('partner') && {
      header: 'Partner',
      cell: (row) => <PartnerLink id={row.partnerProfileId} name={row.partnerName} />
    },
    {
      header: 'Service',
      cell: (row) => (
        <span className="block max-w-[11rem] truncate" title={serviceNameOf(row.serviceName)}>
          {serviceNameOf(row.serviceName)}
        </span>
      )
    },
    { header: 'Partner Rating', cell: (row) => <Stars rating={row.partnerRating} label="Partner rating" /> },
    { header: 'Service Rating', cell: (row) => <Stars rating={row.serviceRating} label="Service rating" /> },
    {
      header: 'Review',
      className: 'max-w-0 w-[24%]',
      cell: (row) =>
        row.reviewText ? (
          <span className="block truncate text-[var(--muted)]" title={row.reviewText}>
            {row.reviewText}
          </span>
        ) : (
          <span className="text-[#A8B6C6]">—</span>
        )
    },
    {
      header: 'Status',
      cell: (row) => (
        <span className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${supportBadgeClass('REVIEW_STATUS', row.reviewStatus)}`}>
          {label('REVIEW_STATUS', row.reviewStatus)}
        </span>
      )
    },
    { header: 'Reviewed', cell: (row) => <When value={row.creationDate} /> },
    canWrite && {
      header: 'Actions',
      headClassName: 'text-right',
      className: 'text-right',
      cell: (row) => (
        <div className="flex justify-end">
          <RowActions
            label={`review of ${row.bookingPublicId}`}
            actions={STATUS_ACTIONS.filter((action) => action.status !== row.reviewStatus).map((action) => ({
              label: action.label,
              icon: action.icon,
              onClick: () => onSetStatus(row, action.status)
            }))}
          />
        </div>
      )
    }
  ].filter(Boolean);

  return (
    <PagedTable
      path={`/api/reviews${query}`}
      columns={columns}
      rowKey={(row) => row.reviewId}
      reloadKey={reloadKey}
      emptyText="No reviews yet."
      minWidth="64rem"
    />
  );
}
