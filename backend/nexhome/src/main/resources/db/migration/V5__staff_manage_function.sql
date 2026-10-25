-- Listing admins and sub-admins is limited to SUPER_USER (always allowed) and ADMIN. Customers and partners are
-- listed with USER_MANAGE read, which SUB_ADMIN also has.
INSERT INTO nexhome_functions (function_module, function_code, function_name, function_type, description,
                               created_by, updated_by)
VALUES ('IDENTITY', 'STAFF_MANAGE', 'Manage Staff', 'FEATURE',
        'Read: list admins and sub-admins.', 1, 1);

INSERT INTO nexhome_role_functions (role_id, function_id, can_read, can_write, can_delete, created_by, updated_by)
SELECT r.id, f.id, TRUE, FALSE, FALSE, 1, 1
FROM nexhome_roles r
JOIN nexhome_functions f ON f.function_code = 'STAFF_MANAGE'
WHERE r.code = 'ADMIN';
