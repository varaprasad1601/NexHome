-- Sub-admins see the admin and sub-admin lists and staff profiles (read only; super users stay hidden from them).
-- Creating staff, changing their status and editing other staff profiles still need USER_MANAGE / STAFF_MANAGE write.
INSERT INTO nexhome_role_functions (role_id, function_id, can_read, can_write, can_delete, created_by, updated_by)
SELECT r.id, f.id, TRUE, FALSE, FALSE, 1, 1
FROM nexhome_roles r
JOIN nexhome_functions f ON f.function_code = 'STAFF_MANAGE'
WHERE r.code = 'SUB_ADMIN';
