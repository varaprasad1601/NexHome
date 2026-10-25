-- First real super user, who signs in with a LOGIN OTP and can then manage everything else.
-- The number is verified (account_verified_date) on that first login.
INSERT INTO nexhome_users (country_code, mobile_number, account_status, created_by, updated_by)
VALUES ('+91', '8897182017', 'ACTIVE', 1, 1)
ON CONFLICT (country_code, mobile_number) DO NOTHING;

INSERT INTO nexhome_user_roles (user_id, role_id, assigned_by, created_by, updated_by)
SELECT u.id, r.id, 1, 1, 1
FROM nexhome_users u
JOIN nexhome_roles r ON r.code = 'SUPER_USER'
WHERE u.country_code = '+91' AND u.mobile_number = '8897182017'
ON CONFLICT (user_id, role_id) DO UPDATE SET is_active = TRUE, updated_by = 1, updated_date = now();
