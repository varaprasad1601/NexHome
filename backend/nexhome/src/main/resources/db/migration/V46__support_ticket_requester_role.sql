-- Which app a support ticket came from, so the requester is shown in that role: CUSTOMER (customer app), PARTNER
-- (partner app) or STAFF (a staff member's own ticket from the console). An account holding both CUSTOMER and
-- PARTNER would otherwise be shown by whichever role ranks higher.
ALTER TABLE nexhome_support_tickets
	ADD COLUMN requester_role VARCHAR(20);

-- Existing tickets: a customer is taken as the customer app, else a partner, else staff
UPDATE nexhome_support_tickets t
SET requester_role = CASE
	WHEN EXISTS (SELECT 1 FROM nexhome_user_roles ur JOIN nexhome_roles r ON r.id = ur.role_id
		WHERE ur.user_id = t.requester_id AND r.code = 'CUSTOMER') THEN 'CUSTOMER'
	WHEN EXISTS (SELECT 1 FROM nexhome_user_roles ur JOIN nexhome_roles r ON r.id = ur.role_id
		WHERE ur.user_id = t.requester_id AND r.code = 'PARTNER') THEN 'PARTNER'
	ELSE 'STAFF'
END;

ALTER TABLE nexhome_support_tickets
	ALTER COLUMN requester_role SET NOT NULL,
	ADD CONSTRAINT ck_nexhome_support_tickets_requester_role
		CHECK (requester_role IN ('CUSTOMER', 'PARTNER', 'STAFF'));
