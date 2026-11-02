-- FAQ_AUDIENCE gets WEB: FAQs for the public website (not the customer or partner app). The public FAQ list returns
-- WEB FAQs only for audience=WEB (CUSTOMER and PARTNER keep returning BOTH as well). BOTH is shown as
-- "Customer & Partner".
INSERT INTO nexhome_lookup_values (lookup_type_id, value_code, display_name, description, display_order, metadata,
		created_by, updated_by)
SELECT lt.id, 'WEB', 'Web', 'Shown on the public website', 0, '{}'::jsonb, 1, 1
FROM nexhome_lookup_types lt
WHERE lt.type_code = 'FAQ_AUDIENCE';

UPDATE nexhome_lookup_values lv
SET display_name = 'Customer & Partner', updated_date = now()
FROM nexhome_lookup_types lt
WHERE lv.lookup_type_id = lt.id AND lt.type_code = 'FAQ_AUDIENCE' AND lv.value_code = 'BOTH';
