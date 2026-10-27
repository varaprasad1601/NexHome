-- ---------------------------------------------------------------------------------------------------------------
-- Staff added from the admin console are created with their name only (Add Admin form): the registered address of
-- a staff profile becomes optional, filled in later. The PDF marks it NOT NULL; it is now all four required parts
-- or none of them.
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE nexhome_admin_profiles
	ALTER COLUMN address_line1 DROP NOT NULL,
	ALTER COLUMN city DROP NOT NULL,
	ALTER COLUMN state DROP NOT NULL,
	ALTER COLUMN postal_code DROP NOT NULL,
	ADD CONSTRAINT ck_nexhome_admin_profiles_address CHECK (
		(address_line1 IS NULL AND city IS NULL AND state IS NULL AND postal_code IS NULL)
		OR (address_line1 IS NOT NULL AND city IS NOT NULL AND state IS NOT NULL AND postal_code IS NOT NULL));
