-- ===============================================================================================================
-- V36: a partner's assignment goes ASSIGNED (staff gave it to the partner) -> ACCEPTED (the partner confirmed it)
-- -> STARTED ("Started Working", the timer started) -> COMPLETED. An offer the partner accepts goes OFFERED ->
-- ACCEPTED as before. ASSIGNED and STARTED are new ASSIGNMENT_STATUS values; the rest are V1's.
-- ===============================================================================================================

INSERT INTO nexhome_lookup_values (lookup_type_id, value_code, display_name, description, display_order, created_by,
		updated_by)
SELECT lt.id, v.value_code, v.display_name, v.description, 0, 1, 1
FROM (VALUES ('ASSIGNED', 'Assigned', 'Given to the partner by staff; waiting for the partner to accept'),
             ('STARTED', 'Started Working', 'The partner started the work')) AS v (value_code, display_name, description)
JOIN nexhome_lookup_types lt ON lt.type_code = 'ASSIGNMENT_STATUS';

-- The statuses in the order a job goes through them
UPDATE nexhome_lookup_values lv
SET display_order = o.position, updated_date = now()
FROM nexhome_lookup_types lt,
     (VALUES ('OFFERED', 1), ('ASSIGNED', 2), ('ACCEPTED', 3), ('STARTED', 4), ('COMPLETED', 5), ('REJECTED', 6),
             ('CANCELLED', 7), ('EXPIRED', 8)) AS o (value_code, position)
WHERE lt.type_code = 'ASSIGNMENT_STATUS' AND lv.lookup_type_id = lt.id AND lv.value_code = o.value_code;

-- One partner holds a booking at a time: assigned, accepted, working or done
DROP INDEX ux_nexhome_booking_assignments_one_accepted;
CREATE UNIQUE INDEX ux_nexhome_booking_assignments_one_holder
	ON nexhome_booking_assignments (booking_id)
	WHERE assignment_status IN ('ASSIGNED', 'ACCEPTED', 'STARTED', 'COMPLETED');
