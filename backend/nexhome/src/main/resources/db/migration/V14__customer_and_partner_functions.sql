-- Manage Customers and Manage Partners become separate functions (the console's two menu items), replacing
-- USER_MANAGE; CUSTOMER_MANAGE also takes over PROFILE_MANAGE (customer profiles and addresses). Every role keeps the
-- access it had: the old grants are copied to the new functions (a flag granted by either old function stays
-- granted), then USER_MANAGE, PROFILE_MANAGE and their grants are removed. Accounts of SUPER_USER and of roles without
-- a function stay with super users.
INSERT INTO nexhome_functions (function_module, function_code, function_name, function_type, description,
                               created_by, updated_by)
VALUES ('USER_MANAGEMENT', 'CUSTOMER_MANAGE', 'Manage Customers', 'FEATURE',
        'Customer accounts, profiles and addresses: read, write (add, status, roles, profiles), delete addresses.',
        1, 1),
       ('USER_MANAGEMENT', 'PARTNER_MANAGE', 'Manage Partners', 'FEATURE',
        'Partner accounts: read lists and counts; write adds them and changes their status and roles.', 1, 1);

INSERT INTO nexhome_role_functions (role_id, function_id, can_read, can_write, can_delete, active_flag,
                                    created_by, updated_by)
SELECT rf.role_id, target.id, bool_or(rf.can_read), bool_or(rf.can_write), bool_or(rf.can_delete),
       bool_or(rf.active_flag), 1, 1
FROM nexhome_role_functions rf
JOIN nexhome_functions old ON old.id = rf.function_id
JOIN (VALUES ('USER_MANAGE', 'CUSTOMER_MANAGE'),
             ('PROFILE_MANAGE', 'CUSTOMER_MANAGE'),
             ('USER_MANAGE', 'PARTNER_MANAGE')) AS m (old_code, new_code) ON m.old_code = old.function_code
JOIN nexhome_functions target ON target.function_code = m.new_code
GROUP BY rf.role_id, target.id;

DELETE FROM nexhome_role_functions
WHERE function_id IN (SELECT id FROM nexhome_functions WHERE function_code IN ('USER_MANAGE', 'PROFILE_MANAGE'));

DELETE FROM nexhome_functions WHERE function_code IN ('USER_MANAGE', 'PROFILE_MANAGE');
