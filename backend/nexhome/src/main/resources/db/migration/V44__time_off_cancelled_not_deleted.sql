-- Time off is cancelled, never deleted: the record stays with who cancelled it, when and why. Cancelled time off no
-- longer makes the partner unavailable, so it is left out of the no-overlap rule. (Leave already under way is ended
-- early instead: its end becomes the moment it was cancelled.)
ALTER TABLE nexhome_partner_time_off
	ADD COLUMN cancelled_at        TIMESTAMPTZ,
	ADD COLUMN cancelled_by        BIGINT REFERENCES nexhome_users (id),
	ADD COLUMN cancellation_reason VARCHAR(200),
	ADD CONSTRAINT ck_nexhome_partner_time_off_cancelled CHECK ((cancelled_at IS NULL) = (cancelled_by IS NULL)),
	ADD CONSTRAINT ck_nexhome_partner_time_off_cancellation_reason
		CHECK (cancellation_reason IS NULL OR cancelled_at IS NOT NULL);

ALTER TABLE nexhome_partner_time_off
	DROP CONSTRAINT ex_nexhome_partner_time_off_overlap;

ALTER TABLE nexhome_partner_time_off
	ADD CONSTRAINT ex_nexhome_partner_time_off_overlap
		EXCLUDE USING gist (partner_id WITH =, tstzrange(starts_at, ends_at) WITH &&) WHERE (cancelled_at IS NULL);
