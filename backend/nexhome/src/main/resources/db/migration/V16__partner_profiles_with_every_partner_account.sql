-- ---------------------------------------------------------------------------------------------------------------
-- Every partner account gets its partner profile, and so its NHPART public id (the partner id), when it gets the
-- PARTNER role (sign-up, Create user, role assignment): an empty DRAFT that the partner fills in during onboarding.
-- The names therefore become optional for a DRAFT, as the other onboarding fields already are, and the submitted
-- check requires them like the rest once the profile leaves DRAFT.
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE nexhome_partner_profiles
	ALTER COLUMN first_name DROP NOT NULL,
	ALTER COLUMN last_name DROP NOT NULL;

ALTER TABLE nexhome_partner_profiles DROP CONSTRAINT ck_nexhome_partner_profiles_submitted;
ALTER TABLE nexhome_partner_profiles ADD CONSTRAINT ck_nexhome_partner_profiles_submitted
	CHECK (onboarding_status = 'DRAFT' OR (
		first_name IS NOT NULL AND last_name IS NOT NULL
		AND is_adult IS NOT NULL AND preferred_contact_language IS NOT NULL AND address_line1 IS NOT NULL
		AND locality IS NOT NULL AND city IS NOT NULL AND state IS NOT NULL AND postal_code IS NOT NULL
		AND skill_category IS NOT NULL AND years_experience IS NOT NULL AND main_service_category_id IS NOT NULL
		AND preferred_arrangement IS NOT NULL AND payment_basis IS NOT NULL AND expected_earnings_inr IS NOT NULL
		AND max_travel_km IS NOT NULL AND weekend_availability IS NOT NULL AND jsonb_array_length(service_areas) > 0));

-- Partners without a profile yet get their DRAFT, oldest account first so the public ids follow the accounts' order.
-- The registration date is the day the account was created (in IST), registered by whoever created the account.
-- User 1 is the system account that owns seed data, not a person.
INSERT INTO nexhome_partner_profiles (user_id, registration_date, registered_by, onboarding_status,
                                      verification_status, availability_status, created_by, updated_by)
SELECT u.id, (u.creation_date AT TIME ZONE 'Asia/Kolkata')::date, COALESCE(u.created_by, 1), 'DRAFT', 'PENDING',
       'OFFLINE', 1, 1
FROM nexhome_users u
WHERE u.id <> 1
	AND EXISTS (
		SELECT 1
		FROM nexhome_user_roles ur
		JOIN nexhome_roles r ON r.id = ur.role_id
		WHERE ur.user_id = u.id
			AND ur.is_active
			AND r.code = 'PARTNER')
	AND NOT EXISTS (SELECT 1 FROM nexhome_partner_profiles p WHERE p.user_id = u.id)
ORDER BY u.id;
