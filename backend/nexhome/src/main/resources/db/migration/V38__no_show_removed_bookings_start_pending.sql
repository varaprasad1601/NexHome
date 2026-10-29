-- BOOKING_STATUS loses NO_SHOW: a partner or customer who does not turn up is handled by cancelling the booking
-- (with its reason). Every new booking now starts PENDING (cash ones too) until staff confirm it; that needs no data
-- change.

-- Bookings that were NO_SHOW become CANCELLED, keeping their reason or saying why
UPDATE nexhome_bookings
SET booking_status = 'CANCELLED',
	cancellation_reason = coalesce(cancellation_reason, 'No show'),
	updated_date = now()
WHERE booking_status = 'NO_SHOW';

-- Their history reads the same way
UPDATE nexhome_booking_status_history
SET new_status = 'CANCELLED',
	reason = coalesce(reason, 'No show'),
	updated_date = now()
WHERE new_status = 'NO_SHOW';

UPDATE nexhome_booking_status_history
SET old_status = 'CANCELLED',
	updated_date = now()
WHERE old_status = 'NO_SHOW';

DELETE FROM nexhome_lookup_values lv
USING nexhome_lookup_types lt
WHERE lt.id = lv.lookup_type_id AND lt.type_code = 'BOOKING_STATUS' AND lv.value_code = 'NO_SHOW';
