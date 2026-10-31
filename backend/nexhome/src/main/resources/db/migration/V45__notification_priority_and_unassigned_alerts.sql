-- Notifications have a priority: NORMAL, or HIGH for what staff should act on now (an offer that expired, a booking
-- about to start without a partner, a failed payment or payout, disputed cash, an urgent ticket). The console lists
-- HIGH ones first with a tag.
ALTER TABLE nexhome_notifications
	ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
	ADD CONSTRAINT ck_nexhome_notifications_priority CHECK (priority IN ('NORMAL', 'HIGH'));

-- When staff were warned that a CONFIRMED booking starts soon without a partner (once per booking)
ALTER TABLE nexhome_bookings
	ADD COLUMN unassigned_alert_at TIMESTAMPTZ;

CREATE INDEX ix_nexhome_bookings_unassigned_soon ON nexhome_bookings (scheduled_start_at)
	WHERE booking_status = 'CONFIRMED' AND partner_id IS NULL AND unassigned_alert_at IS NULL;
