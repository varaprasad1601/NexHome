-- Cash and online bookings share the payment statuses: a cash booking's payment is PENDING until the partner collects
-- the cash (then PAID), as an online one is until it is paid. BOOKING_PAYMENT_STATUS loses CASH_DUE; the payment
-- method tells the two apart. (The cash collection's own status keeps DUE.)
UPDATE nexhome_bookings
SET payment_status = 'PENDING',
	updated_date = now()
WHERE payment_status = 'CASH_DUE';

DELETE FROM nexhome_lookup_values lv
USING nexhome_lookup_types lt
WHERE lt.id = lv.lookup_type_id AND lt.type_code = 'BOOKING_PAYMENT_STATUS' AND lv.value_code = 'CASH_DUE';
