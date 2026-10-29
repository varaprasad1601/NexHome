-- ---------------------------------------------------------------------------------------------------------------
-- The admin console's service form: what a service is for (an appliance or a home), area-based pricing, discount
-- prices, images stored in the database, and requirements for the customer and the partner chosen from lists.
-- ---------------------------------------------------------------------------------------------------------------

-- PER_UNIT becomes AREA: priced by home size, each variant a size (1 BHK, 2 BHK, ...) charged once
UPDATE nexhome_lookup_values lv
SET value_code   = 'AREA',
    display_name = 'Area Based',
    description  = 'Priced by the size of the home; each variant is a size, e.g. 2 BHK',
    updated_by   = 1,
    updated_date = now()
FROM nexhome_lookup_types lt
WHERE lt.id = lv.lookup_type_id
  AND lt.type_code = 'PRICING_TYPE'
  AND lv.value_code = 'PER_UNIT';

UPDATE nexhome_services
SET pricing_type = 'AREA', updated_by = 1, updated_date = now()
WHERE pricing_type = 'PER_UNIT';

-- ---------------------------------------------------------------------------------------------------------------
-- New lookups (not in the design). SERVICE_TARGET: what a service works on; its metadata lists the pricing types
-- such a service may use. CUSTOMER_REQUIREMENT / PARTNER_REQUIREMENT: the options of the service form's requirement
-- lists; metadata.requirementType is the REQUIREMENT_TYPE stored with a chosen option.
-- ---------------------------------------------------------------------------------------------------------------
INSERT INTO nexhome_lookup_types (type_code, type_name, description, created_by, updated_by)
VALUES ('SERVICE_TARGET', 'Service For', 'What a service works on; decides the pricing types it may use', 1, 1),
       ('CUSTOMER_REQUIREMENT', 'Customer Requirement', 'What the customer arranges before a service', 1, 1),
       ('PARTNER_REQUIREMENT', 'Partner Requirement', 'What the partner brings or needs for a service', 1, 1);

INSERT INTO nexhome_lookup_values (lookup_type_id, value_code, display_name, display_order, metadata, created_by,
                                   updated_by)
SELECT lt.id, v.value_code, v.display_name, v.display_order, v.metadata::jsonb, 1, 1
FROM (VALUES ('SERVICE_TARGET', 'APPLIANCE', 'Appliance / Device', 1, '{"pricingTypes": ["FIXED", "HOURLY"]}'),
             ('SERVICE_TARGET', 'PROPERTY', 'Home / Property', 2, '{"pricingTypes": ["FIXED", "HOURLY", "AREA"]}'),
             ('CUSTOMER_REQUIREMENT', 'WATER_SUPPLY', 'Water supply available', 1,
              '{"requirementType": "PREPARATION"}'),
             ('CUSTOMER_REQUIREMENT', 'POWER_SUPPLY', 'Power supply and a free socket', 2,
              '{"requirementType": "PREPARATION"}'),
             ('CUSTOMER_REQUIREMENT', 'CLEAR_WORK_AREA', 'Work area cleared', 3, '{"requirementType": "PREPARATION"}'),
             ('CUSTOMER_REQUIREMENT', 'PARKING', 'Parking for the partner''s vehicle', 4,
              '{"requirementType": "ACCESS"}'),
             ('CUSTOMER_REQUIREMENT', 'GATE_ACCESS', 'Gate or society entry arranged', 5, '{"requirementType": "ACCESS"}'),
             ('CUSTOMER_REQUIREMENT', 'ADULT_PRESENT', 'An adult present during the service', 6,
              '{"requirementType": "SAFETY"}'),
             ('CUSTOMER_REQUIREMENT', 'PETS_SECURED', 'Pets kept away from the work area', 7,
              '{"requirementType": "SAFETY"}'),
             ('CUSTOMER_REQUIREMENT', 'LADDER', 'Ladder available', 8, '{"requirementType": "MATERIALS"}'),
             ('PARTNER_REQUIREMENT', 'OWN_TOOLS', 'Carries own tool kit', 1, '{"requirementType": "MATERIALS"}'),
             ('PARTNER_REQUIREMENT', 'CLEANING_SUPPLIES', 'Brings cleaning supplies', 2,
              '{"requirementType": "MATERIALS"}'),
             ('PARTNER_REQUIREMENT', 'VACUUM_CLEANER', 'Vacuum cleaner', 3, '{"requirementType": "MATERIALS"}'),
             ('PARTNER_REQUIREMENT', 'LADDER', 'Ladder', 4, '{"requirementType": "MATERIALS"}'),
             ('PARTNER_REQUIREMENT', 'SPARE_PARTS', 'Common spare parts', 5, '{"requirementType": "MATERIALS"}'),
             ('PARTNER_REQUIREMENT', 'ELECTRICAL_TESTER', 'Electrical tester', 6, '{"requirementType": "SAFETY"}'),
             ('PARTNER_REQUIREMENT', 'SAFETY_GEAR', 'Safety gear (gloves, mask)', 7, '{"requirementType": "SAFETY"}'),
             ('PARTNER_REQUIREMENT', 'UNIFORM_ID', 'NexHome uniform and ID card', 8, '{"requirementType": "OTHER"}'))
	AS v (type_code, value_code, display_name, display_order, metadata)
JOIN nexhome_lookup_types lt ON lt.type_code = v.type_code;

-- ---------------------------------------------------------------------------------------------------------------
-- Services: what they are for, and a discount price
-- ---------------------------------------------------------------------------------------------------------------
-- SERVICE_TARGET value code; existing services are taken as home services
ALTER TABLE nexhome_services
	ADD COLUMN service_target VARCHAR(60) NOT NULL DEFAULT 'PROPERTY',
	-- Null: no discount; else what customers pay instead of the base price
	ADD COLUMN discount_price NUMERIC(12, 2) CHECK (discount_price >= 0),
	ADD CONSTRAINT ck_nexhome_services_discount CHECK (discount_price IS NULL OR discount_price <= base_price);

ALTER TABLE nexhome_services ALTER COLUMN service_target DROP DEFAULT;

-- A variant's discount needs its own price and is at most that price
ALTER TABLE nexhome_service_variants
	ADD COLUMN discount_price NUMERIC(12, 2) CHECK (discount_price >= 0),
	ADD CONSTRAINT ck_nexhome_service_variants_discount
		CHECK (discount_price IS NULL OR (price_override IS NOT NULL AND discount_price <= price_override));

-- ---------------------------------------------------------------------------------------------------------------
-- Service images, stored in the database until file storage exists (at most 5 per service, by the application)
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE nexhome_service_images (
	id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	service_id    BIGINT       NOT NULL REFERENCES nexhome_services (id) ON DELETE RESTRICT,
	file_name     VARCHAR(255) NOT NULL,
	content_type  VARCHAR(60)  NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
	-- At most 5 MB
	file_size     INTEGER      NOT NULL CHECK (file_size BETWEEN 1 AND 5242880),
	image_data    BYTEA        NOT NULL,
	display_order INTEGER      NOT NULL DEFAULT 0,
	creation_date TIMESTAMPTZ  NOT NULL DEFAULT now(),
	created_by    BIGINT REFERENCES nexhome_users (id),
	updated_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
	updated_by    BIGINT REFERENCES nexhome_users (id),
	attribute1    VARCHAR(255),
	attribute2    VARCHAR(255),
	attribute3    VARCHAR(255),
	attribute4    VARCHAR(255),
	attribute5    VARCHAR(255)
);

CREATE INDEX ix_nexhome_service_images_service_order ON nexhome_service_images (service_id, display_order, id);

-- ---------------------------------------------------------------------------------------------------------------
-- Requirements: for the customer or the partner, optionally one of the lookup options above
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE nexhome_service_requirements
	ADD COLUMN requirement_for  VARCHAR(30) NOT NULL DEFAULT 'CUSTOMER'
		CHECK (requirement_for IN ('CUSTOMER', 'PARTNER')),
	-- CUSTOMER_REQUIREMENT / PARTNER_REQUIREMENT value code (by requirement_for); null for a free-text requirement
	ADD COLUMN requirement_code VARCHAR(60);

CREATE UNIQUE INDEX ux_nexhome_service_requirements_code
	ON nexhome_service_requirements (service_id, requirement_for, requirement_code)
	WHERE requirement_code IS NOT NULL;
