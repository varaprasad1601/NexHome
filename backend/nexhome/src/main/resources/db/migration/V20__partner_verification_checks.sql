-- ---------------------------------------------------------------------------------------------------------------
-- Partner profiles: the profile's own status is its approval status (VERIFICATION_STATUS codes, as before), and
-- staff record three separate checks, each with who made it: the account (identity and contact), the documents and
-- the bank account.
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE nexhome_partner_profiles RENAME COLUMN verification_status TO approval_status;
ALTER INDEX ix_nexhome_partner_profiles_verification_onboarding
	RENAME TO ix_nexhome_partner_profiles_approval_onboarding;

ALTER TABLE nexhome_partner_profiles
	ADD COLUMN account_verified      BOOLEAN NOT NULL DEFAULT FALSE,
	ADD COLUMN account_verified_by   BIGINT REFERENCES nexhome_users (id),
	ADD COLUMN documents_verified    BOOLEAN NOT NULL DEFAULT FALSE,
	ADD COLUMN documents_verified_by BIGINT REFERENCES nexhome_users (id),
	ADD COLUMN bank_acc_verified     BOOLEAN NOT NULL DEFAULT FALSE,
	ADD COLUMN bank_acc_verified_by  BIGINT REFERENCES nexhome_users (id),
	-- A check is verified by someone, and only a verified check names who did it
	ADD CONSTRAINT ck_nexhome_partner_profiles_account_verified
		CHECK (account_verified = (account_verified_by IS NOT NULL)),
	ADD CONSTRAINT ck_nexhome_partner_profiles_documents_verified
		CHECK (documents_verified = (documents_verified_by IS NOT NULL)),
	ADD CONSTRAINT ck_nexhome_partner_profiles_bank_acc_verified
		CHECK (bank_acc_verified = (bank_acc_verified_by IS NOT NULL));
