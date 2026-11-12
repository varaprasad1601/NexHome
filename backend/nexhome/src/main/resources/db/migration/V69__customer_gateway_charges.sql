-- Cashfree's surcharge model: the customer pays Cashfree's service charge and its GST on top of the booking at
-- checkout. Read from Cashfree (payment_surcharge of each payment) and kept apart from NexHome's money: the booking's
-- total, revenue, refunds and the partner's payout never include it.

-- What Cashfree charged the customer on an attempt
ALTER TABLE nexhome_payment_attempts
	ADD COLUMN surcharge_amount NUMERIC(12, 2),
	ADD COLUMN surcharge_tax    NUMERIC(12, 2),
	ADD CONSTRAINT ck_nexhome_payment_attempts_surcharge CHECK (
		(surcharge_amount IS NULL OR surcharge_amount >= 0) AND (surcharge_tax IS NULL OR surcharge_tax >= 0));

-- Each booking's share of it (by amount), on its payment and on the booking
ALTER TABLE nexhome_payments
	ADD COLUMN customer_charges_amount NUMERIC(12, 2),
	ADD CONSTRAINT ck_nexhome_payments_customer_charges CHECK (customer_charges_amount IS NULL
		OR customer_charges_amount >= 0);

ALTER TABLE nexhome_bookings
	ADD COLUMN gateway_service_charge NUMERIC(12, 2) NOT NULL DEFAULT 0,
	ADD COLUMN gateway_service_tax    NUMERIC(12, 2) NOT NULL DEFAULT 0,
	ADD CONSTRAINT ck_nexhome_bookings_gateway_charges CHECK (gateway_service_charge >= 0
		AND gateway_service_tax >= 0);
