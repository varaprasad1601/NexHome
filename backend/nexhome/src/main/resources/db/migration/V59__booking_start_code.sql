-- The start code (not in the PDF): when the partner accepts a booking it gets a 4-digit code that only its customer
-- sees; the partner enters it to start the work (POST /api/bookings/timer START with "otp"), so the work starts only
-- once the partner is with the customer. Kept readable (the customer is shown it); cleared when the work starts, the
-- partner is taken off or the booking is cancelled. Staff starting the work for the partner need no code.
ALTER TABLE nexhome_bookings
	ADD COLUMN start_otp CHAR(4),
	ADD COLUMN start_otp_attempts SMALLINT NOT NULL DEFAULT 0,
	ADD COLUMN start_otp_verified_at TIMESTAMPTZ,
	ADD CONSTRAINT ck_nexhome_bookings_start_otp CHECK (start_otp IS NULL OR start_otp ~ '^[0-9]{4}$'),
	ADD CONSTRAINT ck_nexhome_bookings_start_otp_attempts CHECK (start_otp_attempts >= 0);
