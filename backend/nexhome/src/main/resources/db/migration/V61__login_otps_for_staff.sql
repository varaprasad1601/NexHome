-- Login OTPs shared by staff (temporary, until an SMS provider sends them): the plain code of a customer's or
-- partner's LOGIN / REGISTRATION OTP is kept encrypted (NexHomeFieldEncryptor) while it is pending, so staff can read
-- it out to them. Never for staff accounts or phone changes; cleared once the OTP is verified, expired or locked.
ALTER TABLE nexhome_auth_otp_requests
    ADD COLUMN otp_encrypted TEXT;

INSERT INTO nexhome_functions (function_module, function_code, function_name, function_type, description,
                               created_by, updated_by)
VALUES ('USER_MANAGEMENT', 'LOGIN_OTP_VIEW', 'View Login OTPs', 'FEATURE',
        'Read: the login and sign-up OTPs of customers and partners, to share with them until OTPs are sent by SMS.',
        1, 1);

INSERT INTO nexhome_role_functions (role_id, function_id, can_read, can_write, can_delete, created_by, updated_by)
SELECT r.id, f.id, TRUE, FALSE, FALSE, 1, 1
FROM nexhome_roles r
JOIN nexhome_functions f ON f.function_code = 'LOGIN_OTP_VIEW'
WHERE r.code IN ('ADMIN', 'SUB_ADMIN');
