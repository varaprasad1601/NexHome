-- Why staff rejected a partner's bank account, like a document's rejection reason. Only a REJECTED account has one.
ALTER TABLE nexhome_partner_bank_accounts ADD COLUMN rejection_reason TEXT;

ALTER TABLE nexhome_partner_bank_accounts
	ADD CONSTRAINT ck_nexhome_partner_bank_accounts_rejection_reason
		CHECK (rejection_reason IS NULL OR verification_status = 'REJECTED');
