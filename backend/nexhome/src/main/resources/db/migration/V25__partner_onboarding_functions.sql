-- ---------------------------------------------------------------------------------------------------------------
-- Partner onboarding gets its own module of functions. Manage Partner Onboarding stays the everyday work (view and
-- edit partner profiles, services, documents and bank accounts) and is now granted to sub-admins for writing too;
-- three actions are kept for admins (and super users, who need no grant):
--   PARTNER_VERIFY          Verify Partner: the account check and verifying documents and bank accounts
--   PARTNER_APPROVE         Approve Partner: the onboarding decision (approve, request changes, reject)
--   PARTNER_APPROVED_EDIT   Edit Approved Partners: staff changes to a partner once approved
-- ---------------------------------------------------------------------------------------------------------------
UPDATE nexhome_functions
SET function_module = 'PARTNER_ONBOARDING',
    description     = 'Read: view partner profiles, services, documents and bank accounts. Write: create and update '
                          || 'them until the partner is approved (afterwards PARTNER_APPROVED_EDIT). Delete: remove '
                          || 'partner services, documents and bank accounts.',
    updated_by      = 1,
    updated_date    = now()
WHERE function_code = 'PARTNER_ONBOARDING_MANAGE';

INSERT INTO nexhome_functions (function_module, function_code, function_name, function_type, description,
                               created_by, updated_by)
VALUES ('PARTNER_ONBOARDING', 'PARTNER_VERIFY', 'Verify Partner', 'ACTION',
        'Write: verify a partner''s account, documents and bank accounts (or undo it before approval).', 1, 1),
       ('PARTNER_ONBOARDING', 'PARTNER_APPROVE', 'Approve Partner', 'ACTION',
        'Write: decide on a submitted partner profile: approve, request changes or reject.', 1, 1),
       ('PARTNER_ONBOARDING', 'PARTNER_APPROVED_EDIT', 'Edit Approved Partners', 'ACTION',
        'Write: change an approved partner''s profile, services, documents and bank accounts.', 1, 1);

INSERT INTO nexhome_role_functions (role_id, function_id, can_read, can_write, can_delete, created_by, updated_by)
SELECT r.id, f.id, g.can_read, g.can_write, g.can_delete, 1, 1
FROM (VALUES ('ADMIN', 'PARTNER_VERIFY', TRUE, TRUE, FALSE),
             ('ADMIN', 'PARTNER_APPROVE', TRUE, TRUE, FALSE),
             ('ADMIN', 'PARTNER_APPROVED_EDIT', TRUE, TRUE, FALSE),
             ('SUB_ADMIN', 'PARTNER_ONBOARDING_MANAGE', TRUE, TRUE, FALSE)) AS g (role_code, function_code, can_read,
                                                                                 can_write, can_delete)
JOIN nexhome_roles r ON r.code = g.role_code
JOIN nexhome_functions f ON f.function_code = g.function_code
ON CONFLICT (role_id, function_id) DO UPDATE
    SET can_read     = TRUE,
        can_write    = TRUE,
        active_flag  = TRUE,
        updated_by   = 1,
        updated_date = now();
