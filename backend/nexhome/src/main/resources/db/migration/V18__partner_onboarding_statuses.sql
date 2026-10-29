-- ---------------------------------------------------------------------------------------------------------------
-- Partner onboarding statuses: NOT_STARTED (the empty profile every partner account gets) -> DRAFT ("Profile Draft",
-- once saved, until submitted) -> UNDER_REVIEW -> APPROVED, REJECTED or CHANGES_REQUESTED (returned to the partner
-- for more information or documents; replaces PROFILE_INCOMPLETE). TRAINING_REQUIRED and SUSPENDED are dropped
-- (an approved partner is suspended through the account status).
-- ---------------------------------------------------------------------------------------------------------------

-- The completeness check is replaced below; dropped first, as it rejects NOT_STARTED
ALTER TABLE nexhome_partner_profiles DROP CONSTRAINT ck_nexhome_partner_profiles_submitted;

-- Profiles next, so no row keeps a code that goes away
UPDATE nexhome_partner_profiles
SET onboarding_status = 'NOT_STARTED', updated_by = 1, updated_date = now()
WHERE onboarding_status = 'DRAFT' AND first_name IS NULL;

UPDATE nexhome_partner_profiles
SET onboarding_status = 'CHANGES_REQUESTED', updated_by = 1, updated_date = now()
WHERE onboarding_status = 'PROFILE_INCOMPLETE';

-- Training was asked for while reviewing: back under review
UPDATE nexhome_partner_profiles
SET onboarding_status = 'UNDER_REVIEW', updated_by = 1, updated_date = now()
WHERE onboarding_status = 'TRAINING_REQUIRED';

-- A suspended partner has to be cleared again; the suspension reason stays as the reason for the changes
UPDATE nexhome_partner_profiles
SET onboarding_status = 'CHANGES_REQUESTED', availability_status = 'OFFLINE', updated_by = 1, updated_date = now()
WHERE onboarding_status = 'SUSPENDED';

-- Only NOT_STARTED and DRAFT may be incomplete (the old check, dropped above, allowed DRAFT only)
ALTER TABLE nexhome_partner_profiles ADD CONSTRAINT ck_nexhome_partner_profiles_submitted
	CHECK (onboarding_status IN ('NOT_STARTED', 'DRAFT') OR (
		first_name IS NOT NULL AND last_name IS NOT NULL
		AND is_adult IS NOT NULL AND preferred_contact_language IS NOT NULL AND address_line1 IS NOT NULL
		AND locality IS NOT NULL AND city IS NOT NULL AND state IS NOT NULL AND postal_code IS NOT NULL
		AND skill_category IS NOT NULL AND years_experience IS NOT NULL AND main_service_category_id IS NOT NULL
		AND preferred_arrangement IS NOT NULL AND payment_basis IS NOT NULL AND expected_earnings_inr IS NOT NULL
		AND max_travel_km IS NOT NULL AND weekend_availability IS NOT NULL AND jsonb_array_length(service_areas) > 0));

-- The lookup values
UPDATE nexhome_lookup_values lv
SET value_code = 'CHANGES_REQUESTED',
    display_name = 'Changes Requested',
    description = 'Returned to the partner for more information or documents',
    updated_by = 1,
    updated_date = now()
FROM nexhome_lookup_types lt
WHERE lt.id = lv.lookup_type_id AND lt.type_code = 'PARTNER_ONBOARDING_STATUS'
	AND lv.value_code = 'PROFILE_INCOMPLETE';

UPDATE nexhome_lookup_values lv
SET display_name = 'Profile Draft', description = 'Saved by the partner, not submitted yet', updated_by = 1,
    updated_date = now()
FROM nexhome_lookup_types lt
WHERE lt.id = lv.lookup_type_id AND lt.type_code = 'PARTNER_ONBOARDING_STATUS' AND lv.value_code = 'DRAFT';

DELETE FROM nexhome_lookup_values lv
USING nexhome_lookup_types lt
WHERE lt.id = lv.lookup_type_id AND lt.type_code = 'PARTNER_ONBOARDING_STATUS'
	AND lv.value_code IN ('TRAINING_REQUIRED', 'SUSPENDED');

INSERT INTO nexhome_lookup_values (lookup_type_id, value_code, display_name, description, display_order, created_by,
                                   updated_by)
SELECT lt.id, 'NOT_STARTED', 'Not Started', 'The partner has not filled in the profile yet', 0, 1, 1
FROM nexhome_lookup_types lt
WHERE lt.type_code = 'PARTNER_ONBOARDING_STATUS';

-- Display order follows the lifecycle
UPDATE nexhome_lookup_values lv
SET display_order = o.display_order
FROM nexhome_lookup_types lt,
	(VALUES ('NOT_STARTED', 1), ('DRAFT', 2), ('UNDER_REVIEW', 3), ('CHANGES_REQUESTED', 4), ('APPROVED', 5),
	        ('REJECTED', 6)) AS o (value_code, display_order)
WHERE lt.id = lv.lookup_type_id AND lt.type_code = 'PARTNER_ONBOARDING_STATUS' AND lv.value_code = o.value_code;
