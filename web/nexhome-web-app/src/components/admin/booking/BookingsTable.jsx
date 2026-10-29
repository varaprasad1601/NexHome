import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import Pagination from '../common/Pagination';
import PaymentStatusBadge from './PaymentStatusBadge';
import { headClass, linkClass } from '../system/systemStyles';
import { useAdminAuth } from '../../../context/adminAuth';
import {
  BOOKING_LOOKUPS,
  bookingStatusClass,
  formatAmount,
  serviceNameOf
} from '../../../data/bookingOptions';
import useLookupLabels from '../../../hooks/useLookupLabels';
import { splitApiDateTime } from '../../../utils/format';

const PAGE_SIZE = 10;
const EMPTY_PAGE = { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 };

/**
 * A server-paged list of bookings (GET /api/bookings, BOOKING_MANAGE read) for the given filters (`params`: any of
 * bookingStatus, paymentStatus, customerProfileId, partnerProfileId, serviceId, search). `hide` leaves out the
 * customer or partner column on their own profile. `reloadKey` reloads it; `onLoaded(page)` reports the page.
 */
export default function BookingsTable({ params, hide = [], reloadKey = 0, emptyText = 'No bookings yet.', onLoaded }) {
  const { authRequest } = useAdminAuth();
  const { label } = useLookupLabels(BOOKING_LOOKUPS);
  const [page, setPage] = useState(0);
  const [list, setList] = useState({ data: EMPTY_PAGE, loading: true, error: '', key: '' });
  const query = new URLSearchParams(
    Object.entries(params ?? {}).filter(([, value]) => value !== '' && value !== null && value !== undefined)
  ).toString();

  // A filter change starts again at the first page
  const [lastQuery, setLastQuery] = useState(query);
  if (lastQuery !== query) {
    setLastQuery(query);
    setPage(0);
  }

  useEffect(() => {
    let cancelled = false;
    const search = new URLSearchParams(query);
    search.set('page', String(page));
    search.set('size', String(PAGE_SIZE));
    authRequest(`/api/bookings?${search}`)
      .then((data) => {
        if (cancelled) return;
        setList({ data, loading: false, error: '' });
        onLoaded?.(data);
      })
      .catch((err) => !cancelled && setList({ data: EMPTY_PAGE, loading: false, error: err.message }));
    return () => {
      cancelled = true;
    };
  }, [authRequest, query, page, reloadKey, onLoaded]);

  const rows = list.data.content;
  const columns = 8 - hide.length;

  if (list.error) {
    return (
      <div
        role="alert"
        className="rounded-xl border border-[#F5C2C2] bg-[#FDECEC] px-4 py-3 text-sm font-bold text-[#C53030]"
      >
        {list.error}
      </div>
    );
  }
  return (
    <>
      <div className="overflow-x-auto rounded-xl border border-[var(--line)]">
        <table className="w-full min-w-[60rem] text-sm">
          <thead className="bg-[var(--light)]">
            <tr>
              <th className={`${headClass} w-12`}>#</th>
              <th className={`${headClass} w-[1%]`}>Booking ID</th>
              <th className={headClass}>Service</th>
              {!hide.includes('customer') && <th className={headClass}>Customer</th>}
              {!hide.includes('partner') && <th className={headClass}>Partner</th>}
              <th className={`${headClass} min-w-[11rem]`}>Scheduled</th>
              <th className={headClass}>Status</th>
              <th className={headClass}>Payment</th>
              <th className={`${headClass} text-right`}>Total</th>
            </tr>
          </thead>
          <tbody>
            {list.loading &&
              Array.from({ length: 4 }, (_, i) => (
                <tr key={`loading-${i}`} className="border-t border-[var(--line)]">
                  <td colSpan={columns + 1} className="px-3 py-4">
                    <div className="h-4 animate-pulse rounded bg-[var(--light)]" />
                  </td>
                </tr>
              ))}
            {!list.loading && rows.length === 0 && (
              <tr className="border-t border-[var(--line)]">
                <td colSpan={columns + 1} className="px-3 py-12 text-center text-sm text-[var(--muted)]">
                  {emptyText}
                </td>
              </tr>
            )}
            {!list.loading &&
              rows.map((booking, i) => {
                const when = splitApiDateTime(booking.scheduledStartAt);
                return (
                  <tr
                    key={booking.bookingId}
                    className="border-t border-[var(--line)] text-[var(--ink)] transition hover:bg-[#FAFCFE]"
                  >
                    <td className="px-3 py-3 text-[var(--muted)]">{list.data.page * list.data.size + i + 1}</td>
                    <td className="whitespace-nowrap px-3 py-3">
                      <Link to={`/admin/bookings/${booking.bookingId}`} className={linkClass}>
                        {booking.publicId}
                      </Link>
                    </td>
                    <td className="px-3 py-3">
                      <span className="block max-w-[13rem] truncate font-bold" title={serviceNameOf(booking)}>
                        {serviceNameOf(booking)}
                      </span>
                    </td>
                    {!hide.includes('customer') && (
                      <td className="whitespace-nowrap px-3 py-3">
                        <Link to={`/admin/users/customers/${booking.customerProfileId}`} className={linkClass}>
                          {booking.customerName ?? booking.customerPublicId}
                        </Link>
                      </td>
                    )}
                    {!hide.includes('partner') && (
                      <td className="whitespace-nowrap px-3 py-3">
                        {booking.partnerProfileId ? (
                          <Link to={`/admin/users/partners/${booking.partnerProfileId}`} className={linkClass}>
                            {booking.partnerName ?? booking.partnerPublicId}
                          </Link>
                        ) : (
                          <span className="text-[var(--muted)]">Not assigned</span>
                        )}
                      </td>
                    )}
                    <td className="whitespace-nowrap px-3 py-3">
                      {when ? (
                        <>
                          <span className="font-bold">{when.date}</span>
                          <span className="ml-2 text-[var(--muted)]">{when.time}</span>
                        </>
                      ) : (
                        // An INSTANT booking has no scheduled time
                        <span className="text-[var(--muted)]">As soon as possible</span>
                      )}
                    </td>
                    <td className="px-3 py-3">
                      <span
                        className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[0.75rem] font-bold ${bookingStatusClass(booking.bookingStatus)}`}
                      >
                        {label('BOOKING_STATUS', booking.bookingStatus) ?? booking.bookingStatus}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-3 py-3">
                      <PaymentStatusBadge
                        status={booking.paymentStatus}
                        statusLabel={label('BOOKING_PAYMENT_STATUS', booking.paymentStatus)}
                        method={booking.paymentMethod}
                        methodLabel={label('PAYMENT_METHOD', booking.paymentMethod)}
                      />
                    </td>
                    <td className="whitespace-nowrap px-3 py-3 text-right font-black">
                      {formatAmount(booking.totalAmount)}
                    </td>
                  </tr>
                );
              })}
          </tbody>
        </table>
      </div>
      {!list.loading && <Pagination pageData={list.data} noun="bookings" onPageChange={setPage} />}
    </>
  );
}
