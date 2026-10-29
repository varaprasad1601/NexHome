-- The partner's emergency contact is always shown to staff: the sharing permission goes.
ALTER TABLE nexhome_partner_profiles DROP COLUMN emergency_contact_permission;
