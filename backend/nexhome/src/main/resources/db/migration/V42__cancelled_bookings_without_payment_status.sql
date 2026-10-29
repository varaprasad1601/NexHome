-- A cancelled booking has no payment status: what was paid and refunded stays in its payments and refunds. Every
-- other booking keeps one (BOOKING_PAYMENT_STATUS).
ALTER TABLE nexhome_bookings
	ALTER COLUMN payment_status DROP NOT NULL;

UPDATE nexhome_bookings
SET payment_status = NULL,
	updated_date = now()
WHERE booking_status = 'CANCELLED';

ALTER TABLE nexhome_bookings
	ADD CONSTRAINT ck_nexhome_bookings_payment_status
		CHECK ((booking_status = 'CANCELLED') = (payment_status IS NULL));
