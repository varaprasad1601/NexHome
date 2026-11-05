-- POLICY_KEY gets the policies beside the terms (kind POLICY): the login, registration and app policy of each app.
-- The apps link Terms to the *_TERMS key and Policies to the *_POLICY key of the same place.
INSERT INTO nexhome_lookup_values (lookup_type_id, value_code, display_name, display_order, metadata, created_by,
                                   updated_by)
SELECT lt.id, v.value_code, v.display_name, v.position, v.metadata::jsonb, 1, 1
FROM nexhome_lookup_types lt
CROSS JOIN (VALUES
	('CUSTOMER_LOGIN_POLICY', 'Customer Login Policy', 7, '{"audience": "CUSTOMER", "kind": "POLICY"}'),
	('PARTNER_LOGIN_POLICY', 'Partner Login Policy', 8, '{"audience": "PARTNER", "kind": "POLICY"}'),
	('CUSTOMER_REGISTRATION_POLICY', 'Customer Registration Policy', 9, '{"audience": "CUSTOMER", "kind": "POLICY"}'),
	('PARTNER_REGISTRATION_POLICY', 'Partner Registration Policy', 10, '{"audience": "PARTNER", "kind": "POLICY"}'),
	('CUSTOMER_APP_POLICY', 'Customer App Policy', 11, '{"audience": "CUSTOMER", "kind": "POLICY"}'),
	('PARTNER_APP_POLICY', 'Partner App Policy', 12, '{"audience": "PARTNER", "kind": "POLICY"}')
) AS v (value_code, display_name, position, metadata)
WHERE lt.type_code = 'POLICY_KEY'
  AND NOT EXISTS (SELECT 1 FROM nexhome_lookup_values x WHERE x.lookup_type_id = lt.id AND x.value_code = v.value_code);
