-- The booking status PARTNER_EN_ROUTE is shown as "Partner Accepted" everywhere (the console reads status names from
-- this lookup). The code stays, so bookings, their history and the partner-app endpoints are unchanged.
UPDATE nexhome_lookup_values lv
SET display_name = 'Partner Accepted',
	updated_date = now()
FROM nexhome_lookup_types lt
WHERE lt.id = lv.lookup_type_id AND lt.type_code = 'BOOKING_STATUS' AND lv.value_code = 'PARTNER_EN_ROUTE';
