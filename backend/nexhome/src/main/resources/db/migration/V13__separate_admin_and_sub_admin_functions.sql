-- Manage Admins and Manage Sub Admins become separate functions (the console's two menu items), replacing
-- STAFF_MANAGE. Every role keeps the access it had: its STAFF_MANAGE grant is copied to both new functions, then
-- STAFF_MANAGE and its grants are removed. Super users' accounts stay manageable by super users only (no function).
INSERT INTO nexhome_functions (function_module, function_code, function_name, function_type, description,
                               created_by, updated_by)
VALUES ('USER_MANAGEMENT', 'ADMIN_MANAGE', 'Manage Admins', 'FEATURE',
        'ADMIN accounts: read lists, counts and profiles; write adds them, changes their status and profiles.', 1, 1),
       ('USER_MANAGEMENT', 'SUB_ADMIN_MANAGE', 'Manage Sub Admins', 'FEATURE',
        'SUB_ADMIN accounts: read lists, counts and profiles; write adds them, changes their status and profiles.', 1, 1);

INSERT INTO nexhome_role_functions (role_id, function_id, can_read, can_write, can_delete, active_flag,
                                    created_by, updated_by)
SELECT rf.role_id, f.id, rf.can_read, rf.can_write, rf.can_delete, rf.active_flag, 1, 1
FROM nexhome_role_functions rf
JOIN nexhome_functions staff ON staff.id = rf.function_id AND staff.function_code = 'STAFF_MANAGE'
CROSS JOIN nexhome_functions f
WHERE f.function_code IN ('ADMIN_MANAGE', 'SUB_ADMIN_MANAGE');

DELETE FROM nexhome_role_functions
WHERE function_id = (SELECT id FROM nexhome_functions WHERE function_code = 'STAFF_MANAGE');

DELETE FROM nexhome_functions WHERE function_code = 'STAFF_MANAGE';
