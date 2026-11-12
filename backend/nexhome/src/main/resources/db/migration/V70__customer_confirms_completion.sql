-- The customer confirms an online job's work is done (not in the PDF): the partner's Finish work asks
-- (completion_requested_at; cleared if the customer says not yet), the customer's confirmation stops the work, prices
-- it and completes the booking with the payment pending (completion_confirmed_at).
ALTER TABLE nexhome_bookings
	ADD COLUMN completion_requested_at TIMESTAMPTZ,
	ADD COLUMN completion_confirmed_at TIMESTAMPTZ;
