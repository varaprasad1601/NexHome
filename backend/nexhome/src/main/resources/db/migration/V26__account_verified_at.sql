-- When staff verified the partner's account, next to who did it (the onboarding screen's audit trail). Rows already
-- verified take their last update as the best known time.
ALTER TABLE nexhome_partner_profiles ADD COLUMN account_verified_at TIMESTAMPTZ;

UPDATE nexhome_partner_profiles SET account_verified_at = updated_date WHERE account_verified;

ALTER TABLE nexhome_partner_profiles
	ADD CONSTRAINT ck_nexhome_partner_profiles_account_verified_at
		CHECK (account_verified = (account_verified_at IS NOT NULL));
