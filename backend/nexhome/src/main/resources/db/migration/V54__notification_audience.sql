-- Which app a notification belongs to (not in the PDF): a user who is a customer, a partner and staff at once sees
-- each notification only in its own app. CUSTOMER: the customer app; PARTNER: the partner app; STAFF: the admin
-- console; ALL: every app (a note staff sent to one user).
ALTER TABLE nexhome_notifications ADD COLUMN audience VARCHAR(20);

-- Existing notifications: from what they are about and who got them (as NexHomeNotifier.audienceOf does now)
UPDATE nexhome_notifications n SET audience = (
	SELECT CASE
		WHEN cp.user_id = n.user_id THEN 'CUSTOMER'
		WHEN pp.user_id = n.user_id THEN 'PARTNER'
		WHEN EXISTS (SELECT 1 FROM nexhome_booking_assignments a
				JOIN nexhome_partner_profiles ap ON ap.id = a.partner_id
				WHERE a.booking_id = b.id AND ap.user_id = n.user_id) THEN 'PARTNER'
		ELSE 'STAFF' END
	FROM nexhome_bookings b
	JOIN nexhome_customer_profiles cp ON cp.id = b.customer_id
	LEFT JOIN nexhome_partner_profiles pp ON pp.id = b.partner_id
	WHERE b.id = n.reference_id)
WHERE n.reference_type = 'BOOKING';

UPDATE nexhome_notifications n SET audience = (
	SELECT CASE WHEN t.requester_id = n.user_id THEN t.requester_role ELSE 'STAFF' END
	FROM nexhome_support_tickets t WHERE t.id = n.reference_id)
WHERE n.reference_type = 'SUPPORT_TICKET';

UPDATE nexhome_notifications n SET audience = (
	SELECT CASE WHEN pp.user_id = n.user_id THEN 'PARTNER' WHEN cp.user_id = n.user_id THEN 'CUSTOMER' ELSE 'STAFF' END
	FROM nexhome_booking_reviews r
	JOIN nexhome_partner_profiles pp ON pp.id = r.partner_id
	JOIN nexhome_customer_profiles cp ON cp.id = r.customer_id
	WHERE r.id = n.reference_id)
WHERE n.reference_type = 'REVIEW';

UPDATE nexhome_notifications n SET audience = (
	SELECT CASE WHEN p.user_id = n.user_id THEN 'PARTNER' ELSE 'STAFF' END
	FROM nexhome_partner_profiles p WHERE p.id = n.reference_id)
WHERE n.reference_type = 'PARTNER_PROFILE';

UPDATE nexhome_notifications n SET audience = (
	SELECT CASE WHEN p.user_id = n.user_id THEN 'CUSTOMER' ELSE 'STAFF' END
	FROM nexhome_customer_profiles p WHERE p.id = n.reference_id)
WHERE n.reference_type = 'CUSTOMER_PROFILE';

-- Anything else (staff notes without a record, or a record since removed)
UPDATE nexhome_notifications SET audience = 'ALL' WHERE audience IS NULL;

ALTER TABLE nexhome_notifications
	ALTER COLUMN audience SET DEFAULT 'ALL',
	ALTER COLUMN audience SET NOT NULL,
	ADD CONSTRAINT ck_nexhome_notifications_audience CHECK (audience IN ('ALL', 'CUSTOMER', 'PARTNER', 'STAFF'));

CREATE INDEX ix_nexhome_notifications_user_audience ON nexhome_notifications (user_id, audience, creation_date DESC);
