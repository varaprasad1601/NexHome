-- The public website's own terms and privacy policy (not in the PDF): a WEB policy audience ("Website": never asked
-- of app users to accept; GET /api/policies/pending covers CUSTOMER, PARTNER and BOTH only) and two POLICY_KEY values
-- the website's Policies & Terms page (/policies) shows through GET /api/policies/current/{key}.
INSERT INTO nexhome_lookup_values (lookup_type_id, value_code, display_name, display_order, metadata, created_by,
                                   updated_by)
SELECT lt.id, 'WEB', 'Website', COALESCE((SELECT MAX(x.display_order) FROM nexhome_lookup_values x
                                          WHERE x.lookup_type_id = lt.id), 0) + 1, '{}'::jsonb, 1, 1
FROM nexhome_lookup_types lt
WHERE lt.type_code = 'POLICY_AUDIENCE'
  AND NOT EXISTS (SELECT 1 FROM nexhome_lookup_values x WHERE x.lookup_type_id = lt.id AND x.value_code = 'WEB');

INSERT INTO nexhome_lookup_values (lookup_type_id, value_code, display_name, display_order, metadata, created_by,
                                   updated_by)
SELECT lt.id, v.value_code, v.display_name, v.position, v.metadata::jsonb, 1, 1
FROM nexhome_lookup_types lt
CROSS JOIN (VALUES
	('WEB_TERMS', 'Website Terms & Conditions', 13, '{"audience": "WEB", "kind": "TERMS"}'),
	('WEB_POLICY', 'Website Privacy Policy', 14, '{"audience": "WEB", "kind": "POLICY"}')
) AS v (value_code, display_name, position, metadata)
WHERE lt.type_code = 'POLICY_KEY'
  AND NOT EXISTS (SELECT 1 FROM nexhome_lookup_values x WHERE x.lookup_type_id = lt.id AND x.value_code = v.value_code);
