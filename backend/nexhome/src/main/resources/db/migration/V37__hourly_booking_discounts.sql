-- ===============================================================================================================
-- V37: hourly bookings show their discount too. Until now an HOURLY booking stored the discounted price as its
-- subtotal with no discount (e.g. a 2-hour package of 200 discounted to 160: subtotal 160, discount 0). From the
-- price snapshot, the regular amount is the charged subtotal at the regular rate (subtotal x regularPrice /
-- unitPrice): subtotal_amount becomes that, discount_amount the difference; the total does not change.
-- ===============================================================================================================

WITH fixed AS (
	SELECT b.id,
		   (b.pricing_snapshot ->> 'subtotal')::numeric AS charged,
		   round((b.pricing_snapshot ->> 'subtotal')::numeric * (b.pricing_snapshot ->> 'regularPrice')::numeric
				 / (b.pricing_snapshot ->> 'unitPrice')::numeric, 2) AS regular
	FROM nexhome_bookings b
	WHERE b.pricing_snapshot ->> 'pricingType' = 'HOURLY'
	  AND b.discount_amount = 0
	  AND (b.pricing_snapshot ->> 'unitPrice')::numeric > 0
	  AND (b.pricing_snapshot ->> 'regularPrice')::numeric > (b.pricing_snapshot ->> 'unitPrice')::numeric
)
UPDATE nexhome_bookings b
SET subtotal_amount  = fixed.regular,
	discount_amount  = fixed.regular - fixed.charged,
	pricing_snapshot = b.pricing_snapshot || jsonb_build_object('regularSubtotal', fixed.regular),
	updated_date     = now()
FROM fixed
WHERE b.id = fixed.id;
