-- The documents and bank account checks of a partner profile come from the records themselves: every document's
-- verification_status (with its verified_by / verified_at) and the primary bank account's verification_status (with
-- its updated_by / updated_date). Only the account check stays on the profile. The check constraints go with the
-- columns.
ALTER TABLE nexhome_partner_profiles
	DROP COLUMN documents_verified,
	DROP COLUMN documents_verified_by,
	DROP COLUMN bank_acc_verified,
	DROP COLUMN bank_acc_verified_by;
