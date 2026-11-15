-- The partner reached the work area (not in the PDF): before starting the work the partner taps "I've reached the
-- work area" (PUT /api/bookings/arrived); the booking keeps when, the history a line, and the customer is told with a
-- notification of the new type PARTNER_ARRIVED, which the customer app rings with the doorbell sound (its push goes on
-- the apps' `doorbell` channel). Cleared when the partner is taken off; set by START if the partner never tapped it.
ALTER TABLE nexhome_bookings ADD COLUMN partner_arrived_at TIMESTAMPTZ;

INSERT INTO nexhome_lookup_values (lookup_type_id, value_code, display_name, display_order, created_by, updated_by)
SELECT lt.id, 'PARTNER_ARRIVED', 'Partner Arrived', 8, 1, 1
FROM nexhome_lookup_types lt
WHERE lt.type_code = 'NOTIFICATION_TYPE'
	AND NOT EXISTS (SELECT 1 FROM nexhome_lookup_values v WHERE v.lookup_type_id = lt.id AND v.value_code = 'PARTNER_ARRIVED');
