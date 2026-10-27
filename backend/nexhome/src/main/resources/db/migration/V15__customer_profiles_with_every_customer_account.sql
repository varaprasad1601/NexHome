-- ---------------------------------------------------------------------------------------------------------------
-- Every customer account gets its customer profile, and so its NHCUST public id (the customer id), when it gets the
-- CUSTOMER role (sign-up, Create user, role assignment); the customer fills in their name later. The names therefore
-- become optional, as for staff profiles in V9.
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE nexhome_customer_profiles
	ALTER COLUMN first_name DROP NOT NULL,
	ALTER COLUMN last_name DROP NOT NULL;

-- Customers without a profile yet get one, oldest account first so the public ids follow the accounts' order. User 1
-- is the system account that owns seed data, not a person.
INSERT INTO nexhome_customer_profiles (user_id, created_by, updated_by)
SELECT u.id, 1, 1
FROM nexhome_users u
WHERE u.id <> 1
	AND EXISTS (
		SELECT 1
		FROM nexhome_user_roles ur
		JOIN nexhome_roles r ON r.id = ur.role_id
		WHERE ur.user_id = u.id
			AND ur.is_active
			AND r.code = 'CUSTOMER')
	AND NOT EXISTS (SELECT 1 FROM nexhome_customer_profiles p WHERE p.user_id = u.id)
ORDER BY u.id;
