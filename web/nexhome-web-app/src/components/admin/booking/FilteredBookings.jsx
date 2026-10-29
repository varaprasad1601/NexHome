import { RotateCcw, Search } from 'lucide-react';
import { useState } from 'react';

import BookingsTable from './BookingsTable';
import Select from '../common/Select';
import { resetButtonClass, searchInputClass } from '../system/systemStyles';
import { BOOKING_LOOKUPS } from '../../../data/bookingOptions';
import useDebounced from '../../../hooks/useDebounced';
import useLookupLabels from '../../../hooks/useLookupLabels';

/**
 * One customer's or partner's bookings (BOOKING_MANAGE read) with a search and a booking status filter, through
 * GET /api/bookings: the customer profile's Bookings tab and the partner profile's Jobs tab. `params` fixes the
 * owner (customerProfileId / partnerProfileId); `hide` drops that column; `hiddenStatuses` leaves statuses out of the
 * filter; `emptyText` shows when there are none at all.
 */
export default function FilteredBookings({
  params,
  hide = [],
  hiddenStatuses = [],
  searchPlaceholder = 'Search by booking ID or service...',
  emptyText
}) {
  const { values } = useLookupLabels(BOOKING_LOOKUPS);
  const [searchText, setSearchText] = useState('');
  const search = useDebounced(searchText.trim());
  const [bookingStatus, setBookingStatus] = useState('');
  const filtered = Boolean(search || bookingStatus);

  return (
    <>
      <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_13rem_auto]">
        <label className="relative">
          <span className="sr-only">Search bookings</span>
          <Search
            size={16}
            className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--muted)]"
          />
          <input
            type="search"
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            placeholder={searchPlaceholder}
            className={searchInputClass}
          />
        </label>
        <Select
          ariaLabel="Booking status"
          value={bookingStatus}
          onChange={setBookingStatus}
          options={[
            { value: '', label: 'All Status' },
            ...(values.BOOKING_STATUS ?? [])
              .filter((value) => !hiddenStatuses.includes(value.valueCode))
              .map((value) => ({ value: value.valueCode, label: value.displayName }))
          ]}
        />
        <button
          type="button"
          className={resetButtonClass}
          onClick={() => {
            setSearchText('');
            setBookingStatus('');
          }}
        >
          <RotateCcw size={15} />
          Reset
        </button>
      </div>
      <BookingsTable
        params={{ ...params, search, bookingStatus }}
        hide={hide}
        emptyText={filtered ? 'No bookings match your search or filter.' : emptyText}
      />
    </>
  );
}
