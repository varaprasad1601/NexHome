-- Admins edit, verify and approve every partner, whoever registered the partner or verified anything before (only a
-- partner's own profile stays off limits). Restores ADMIN's grants of the partner onboarding functions, whatever they
-- were changed to: Manage Partner Onboarding read / write / delete, Verify Partner, Approve Partner and Edit Approved
-- Partners read / write, all active.
INSERT INTO nexhome_role_functions (role_id, function_id, can_read, can_write, can_delete, created_by, updated_by)
SELECT r.id, f.id, TRUE, TRUE, g.can_delete, 1, 1
FROM (VALUES ('PARTNER_ONBOARDING_MANAGE', TRUE),
             ('PARTNER_VERIFY', FALSE),
             ('PARTNER_APPROVE', FALSE),
             ('PARTNER_APPROVED_EDIT', FALSE)) AS g (function_code, can_delete)
JOIN nexhome_roles r ON r.code = 'ADMIN'
JOIN nexhome_functions f ON f.function_code = g.function_code
ON CONFLICT (role_id, function_id) DO UPDATE
    SET can_read     = TRUE,
        can_write    = TRUE,
        can_delete   = nexhome_role_functions.can_delete OR EXCLUDED.can_delete,
        active_flag  = TRUE,
        updated_by   = 1,
        updated_date = now();

UPDATE nexhome_functions
SET active_flag = TRUE, updated_by = 1, updated_date = now()
WHERE function_code IN ('PARTNER_ONBOARDING_MANAGE', 'PARTNER_VERIFY', 'PARTNER_APPROVE', 'PARTNER_APPROVED_EDIT')
  AND NOT active_flag;
