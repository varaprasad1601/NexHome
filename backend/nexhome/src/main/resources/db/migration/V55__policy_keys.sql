-- POLICY_KEY (not in the PDF): the policies the apps show, so the console picks a policy's key from a list instead of
-- typing it. `metadata.audience` is the app each is for (the console fills Applies To from it); the customer and
-- partner apps open these keys by name (src/constants/policies.js). Super users add more under Lookups.
INSERT INTO nexhome_lookup_types (type_code, type_name, description, created_by, updated_by)
VALUES ('POLICY_KEY', 'Policy Key', 'The policies the apps show: login, registration and app terms for customers and partners',
        1, 1);

INSERT INTO nexhome_lookup_values (lookup_type_id, value_code, display_name, display_order, metadata, created_by,
                                   updated_by)
SELECT lt.id, v.value_code, v.display_name, v.position, v.metadata::jsonb, 1, 1
FROM nexhome_lookup_types lt
CROSS JOIN (VALUES
	('CUSTOMER_LOGIN_TERMS', 'Customer Login Terms & Conditions', 1, '{"audience": "CUSTOMER"}'),
	('PARTNER_LOGIN_TERMS', 'Partner Login Terms & Conditions', 2, '{"audience": "PARTNER"}'),
	('CUSTOMER_REGISTRATION_TERMS', 'Customer Registration Terms & Conditions', 3, '{"audience": "CUSTOMER"}'),
	('PARTNER_REGISTRATION_TERMS', 'Partner Registration Terms & Conditions', 4, '{"audience": "PARTNER"}'),
	('CUSTOMER_APP_TERMS', 'Customer App Terms & Conditions', 5, '{"audience": "CUSTOMER"}'),
	('PARTNER_APP_TERMS', 'Partner App Terms & Conditions', 6, '{"audience": "PARTNER"}')
) AS v (value_code, display_name, position, metadata)
WHERE lt.type_code = 'POLICY_KEY';
