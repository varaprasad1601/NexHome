-- Which app asked for an OTP (V65): CUSTOMER (the customer app), PARTNER (the partner app) or STAFF (the admin
-- console); null for phone changes and older OTPs. The Login OTPs screen shows it as the login's role. Codes of
-- accounts holding a staff role are now kept too, but only super users see them (V61 left them out).
ALTER TABLE nexhome_auth_otp_requests
	ADD COLUMN requested_from VARCHAR(20),
	ADD CONSTRAINT ck_nexhome_auth_otp_requests_requested_from
		CHECK (requested_from IS NULL OR requested_from IN ('CUSTOMER', 'PARTNER', 'STAFF'));
