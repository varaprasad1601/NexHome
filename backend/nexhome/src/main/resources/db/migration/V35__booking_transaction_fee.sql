-- ===============================================================================================================
-- V35: a transaction fee on online bookings (the payment gateway's charge), not in the design.
-- Its rate is the lookup TRANSACTION_FEE, value ONLINE, metadata.percentage (super users change it under System
-- Configuration; 0 turns it off). Each booking keeps the fee it was charged: total = subtotal - discount + tax + fee.
-- The fee is not refunded and is not part of the partner's payout.
-- ===============================================================================================================

INSERT INTO nexhome_lookup_types (type_code, type_name, description, created_by, updated_by)
VALUES ('TRANSACTION_FEE', 'Transaction Fee',
		'Fees added to a booking; metadata.percentage is the rate (ONLINE: online payments)', 1, 1);

INSERT INTO nexhome_lookup_values (lookup_type_id, value_code, display_name, description, display_order, metadata,
		created_by, updated_by)
SELECT lt.id, 'ONLINE', 'Online Payment Fee', 'Added to online bookings; not refunded', 1, '{"percentage": 2}'::jsonb,
		1, 1
FROM nexhome_lookup_types lt
WHERE lt.type_code = 'TRANSACTION_FEE';

ALTER TABLE nexhome_bookings
	ADD COLUMN transaction_fee_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (transaction_fee_amount >= 0);
