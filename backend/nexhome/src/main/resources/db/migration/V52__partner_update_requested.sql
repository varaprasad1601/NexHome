-- PARTNER_ONBOARDING_STATUS gets UPDATE_REQUESTED ("Update Requested"): an approved partner who added or changed a
-- document or bank account (or whose government ID anyone changed) waits here, approval PENDING and not bookable,
-- until staff verify the change and approve again (or reject / request changes). Shown after Approved.
UPDATE nexhome_lookup_values lv
SET display_order = lv.display_order + 1, updated_date = now()
FROM nexhome_lookup_types lt
WHERE lv.lookup_type_id = lt.id AND lt.type_code = 'PARTNER_ONBOARDING_STATUS' AND lv.display_order >= 6;

INSERT INTO nexhome_lookup_values (lookup_type_id, value_code, display_name, description, display_order, metadata,
		created_by, updated_by)
SELECT lt.id, 'UPDATE_REQUESTED', 'Update Requested',
       'An approved partner changed a document or bank account; verify the change and approve again', 6, '{}'::jsonb,
       1, 1
FROM nexhome_lookup_types lt
WHERE lt.type_code = 'PARTNER_ONBOARDING_STATUS';
