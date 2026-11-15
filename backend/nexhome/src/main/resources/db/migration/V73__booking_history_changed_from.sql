-- Which app each booking status change came from (not in the PDF): CUSTOMER, PARTNER, STAFF (the admin console) or
-- SYSTEM (NexHome itself: user 1, payments, scheduled jobs), from the request's X-NexHome-App header. One number may be
-- staff, a partner and a customer, so the user alone does not tell whether a change was the partner's (a decline) or
-- an admin's; the history shows "by Partner" / "by Admin" from this. Null for older rows and older clients.
ALTER TABLE nexhome_booking_status_history ADD COLUMN changed_from VARCHAR(20);

ALTER TABLE nexhome_booking_status_history
	ADD CONSTRAINT ck_nexhome_booking_status_history_changed_from
	CHECK (changed_from IS NULL OR changed_from IN ('CUSTOMER', 'PARTNER', 'STAFF', 'SYSTEM'));

-- Existing rows: NexHome's own changes, and partners declining (always the partner's answer)
UPDATE nexhome_booking_status_history SET changed_from = 'SYSTEM' WHERE changed_by = 1;
UPDATE nexhome_booking_status_history SET changed_from = 'PARTNER'
WHERE reason LIKE 'Partner declined%' AND changed_from IS NULL;
