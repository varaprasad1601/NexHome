-- ===============================================================================================================
-- V34: booking items keep the service variant (option / package) as it was booked, as a JSON snapshot, instead of
-- referencing the variant: the service form replaces variant lists, so the variant may change or be deleted later.
-- ===============================================================================================================

ALTER TABLE nexhome_booking_items ADD COLUMN variant_snapshot JSONB;

UPDATE nexhome_booking_items item
SET variant_snapshot = jsonb_build_object(
		'serviceVariantId', variant.id,
		'publicId', variant.public_id,
		'variantCode', variant.variant_code,
		'name', variant.name,
		'price', variant.price_override,
		'discountPrice', variant.discount_price,
		'durationMinutes', variant.duration_minutes)
FROM nexhome_service_variants variant
WHERE variant.id = item.variant_id;

ALTER TABLE nexhome_booking_items DROP COLUMN variant_id;

ALTER TABLE nexhome_booking_items ADD CONSTRAINT ck_nexhome_booking_items_variant_snapshot
	CHECK (variant_snapshot IS NULL OR jsonb_typeof(variant_snapshot) = 'object');
