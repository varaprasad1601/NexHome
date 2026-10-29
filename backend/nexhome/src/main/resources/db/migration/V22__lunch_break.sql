-- A one-hour lunch break, 12 PM to 1 PM, belongs to no availability slot: the afternoon slot starts at 1 PM.
UPDATE nexhome_lookup_values lv
SET display_name = 'Afternoon (1 PM - 3 PM)',
    metadata     = '{"start": "13:00", "end": "15:00"}'::jsonb,
    updated_by   = 1,
    updated_date = now()
FROM nexhome_lookup_types lt
WHERE lt.id = lv.lookup_type_id AND lt.type_code = 'AVAILABILITY_SLOT' AND lv.value_code = 'AFTERNOON';

-- The break itself, for screens and later scheduling (not a slot partners choose)
UPDATE nexhome_lookup_types
SET description = 'The daily slots of a partner''s weekly availability; 12 PM to 1 PM is a lunch break in no slot',
    updated_by  = 1,
    updated_date = now()
WHERE type_code = 'AVAILABILITY_SLOT';
