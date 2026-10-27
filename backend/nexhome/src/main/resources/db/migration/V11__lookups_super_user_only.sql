-- System Configuration (roles, functions, permissions, lookups) is changed by super users only. ROLE_MANAGE and
-- FUNCTION_MANAGE were already read-only for ADMIN; lookups now are too. ADMIN and SUB_ADMIN keep LOOKUP_MANAGE read.
UPDATE nexhome_role_functions rf
SET can_write    = FALSE,
    can_delete   = FALSE,
    updated_by   = 1,
    updated_date = now()
FROM nexhome_roles r, nexhome_functions f
WHERE rf.role_id = r.id
  AND rf.function_id = f.id
  AND r.code = 'ADMIN'
  AND f.function_code = 'LOOKUP_MANAGE';
