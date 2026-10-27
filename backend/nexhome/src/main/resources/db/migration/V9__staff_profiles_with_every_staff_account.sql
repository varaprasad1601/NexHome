-- ---------------------------------------------------------------------------------------------------------------
-- Every staff account (SUPER_USER, ADMIN, SUB_ADMIN) gets its staff profile, and so its NHADMI public id, when it
-- is created (Create user); the staff member fills in their name later under My Profile. The names therefore
-- become optional, as the address did in V8.
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE nexhome_admin_profiles
	ALTER COLUMN first_name DROP NOT NULL,
	ALTER COLUMN last_name DROP NOT NULL;

-- Staff accounts created before this have no profile yet: give them one, oldest account first so the public ids
-- follow the accounts' order. User 1 is the system account that owns seed data, not a person.
INSERT INTO nexhome_admin_profiles (user_id, created_by, updated_by)
SELECT u.id, 1, 1
FROM nexhome_users u
WHERE u.id <> 1
	AND EXISTS (
		SELECT 1
		FROM nexhome_user_roles ur
		JOIN nexhome_roles r ON r.id = ur.role_id
		WHERE ur.user_id = u.id
			AND ur.is_active
			AND r.code IN ('SUPER_USER', 'ADMIN', 'SUB_ADMIN'))
	AND NOT EXISTS (SELECT 1 FROM nexhome_admin_profiles p WHERE p.user_id = u.id)
ORDER BY u.id;
