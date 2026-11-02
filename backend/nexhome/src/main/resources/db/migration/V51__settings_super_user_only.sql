-- General settings are changed by super users only (the update endpoint checks it too); ADMIN keeps read, like
-- SUB_ADMIN.
UPDATE nexhome_role_functions rf
SET can_write = FALSE, can_delete = FALSE, updated_date = now()
FROM nexhome_functions f
WHERE rf.function_id = f.id AND f.function_code = 'SETTINGS_MANAGE';

UPDATE nexhome_functions
SET description = 'Read: the general settings (business and contact details, booking rules, staff alerts). '
        || 'Only super users change them.',
    updated_date = now()
WHERE function_code = 'SETTINGS_MANAGE';
