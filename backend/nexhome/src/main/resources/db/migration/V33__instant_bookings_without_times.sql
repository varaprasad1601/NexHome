-- An INSTANT booking is taken as soon as possible: it has no scheduled start or end (they stay empty until the work
-- starts, when actual_start_at is recorded). A SCHEDULED booking still needs its start.
ALTER TABLE nexhome_bookings ALTER COLUMN scheduled_start_at DROP NOT NULL;

ALTER TABLE nexhome_bookings
	ADD CONSTRAINT ck_nexhome_bookings_scheduled_start
		CHECK (booking_type <> 'SCHEDULED' OR scheduled_start_at IS NOT NULL);
