-- ---------------------------------------------------------------------------------------------------------------
-- Partner documents as the console takes them: a Government ID with its ID type, or a Skill Certificate with its
-- name; number and optional expiry date. Files are optional until file storage exists.
-- ---------------------------------------------------------------------------------------------------------------

-- New lookup (not in the design): the kinds of government ID
INSERT INTO nexhome_lookup_types (type_code, type_name, description, created_by, updated_by)
VALUES ('GOVERNMENT_ID_TYPE', 'Government ID Type', 'The kinds of government ID a partner can add', 1, 1);

INSERT INTO nexhome_lookup_values (lookup_type_id, value_code, display_name, display_order, created_by, updated_by)
SELECT lt.id, v.value_code, v.display_name, v.display_order, 1, 1
FROM (VALUES ('AADHAAR', 'Aadhaar Card', 1),
             ('PAN', 'PAN Card', 2),
             ('VOTER_ID', 'Voter ID', 3),
             ('DRIVING_LICENCE', 'Driving Licence', 4),
             ('PASSPORT', 'Passport', 5)) AS v (value_code, display_name, display_order)
JOIN nexhome_lookup_types lt ON lt.type_code = 'GOVERNMENT_ID_TYPE';

-- CERTIFICATION documents are skill certificates
UPDATE nexhome_lookup_values lv
SET display_name = 'Skill Certificate', updated_by = 1, updated_date = now()
FROM nexhome_lookup_types lt
WHERE lt.id = lv.lookup_type_id AND lt.type_code = 'DOCUMENT_TYPE' AND lv.value_code = 'CERTIFICATION';

ALTER TABLE nexhome_partner_documents
	-- GOVERNMENT_ID_TYPE value code; required for a GOVERNMENT_ID document
	ADD COLUMN id_type       VARCHAR(60),
	-- e.g. the certificate's name ("ITI Electrician"); required for a CERTIFICATION document
	ADD COLUMN document_name VARCHAR(150),
	ALTER COLUMN file_storage_key DROP NOT NULL,
	ADD CONSTRAINT ck_nexhome_partner_documents_id_type
		CHECK (document_type <> 'GOVERNMENT_ID' OR id_type IS NOT NULL);
