-- Payments show in a booking's history: a row whose status stays the same (old = new) with the reason "Payment
-- received" (an online payment captured) or "Cash collected". From now on the application writes them; this adds them
-- for what was already paid, at the time it was paid. (An online payment that confirmed a PENDING booking already has
-- its PENDING -> CONFIRMED row with that reason.)

INSERT INTO nexhome_booking_status_history (booking_id, old_status, new_status, changed_by, reason, creation_date,
	created_by, updated_date, updated_by)
SELECT p.booking_id, b.booking_status, b.booking_status, p.created_by, 'Payment received', p.captured_at,
	p.created_by, p.captured_at, p.created_by
FROM nexhome_payments p
JOIN nexhome_bookings b ON b.id = p.booking_id
WHERE p.captured_at IS NOT NULL
	AND p.status IN ('CAPTURED', 'PARTIALLY_REFUNDED', 'REFUNDED')
	AND NOT EXISTS (SELECT 1 FROM nexhome_booking_status_history h
		WHERE h.booking_id = p.booking_id AND h.reason = 'Payment received');

INSERT INTO nexhome_booking_status_history (booking_id, old_status, new_status, changed_by, reason, creation_date,
	created_by, updated_date, updated_by)
SELECT c.booking_id, b.booking_status, b.booking_status, c.updated_by, 'Cash collected', c.confirmed_at,
	c.updated_by, c.confirmed_at, c.updated_by
FROM nexhome_cash_collections c
JOIN nexhome_bookings b ON b.id = c.booking_id
WHERE c.status = 'COLLECTED' AND c.confirmed_at IS NOT NULL
	AND NOT EXISTS (SELECT 1 FROM nexhome_booking_status_history h
		WHERE h.booking_id = c.booking_id AND h.reason = 'Cash collected');
