-- The online payment fee is gone (V35's transaction_fee_amount stays for the bookings that paid it; new bookings get
-- 0): customers pay the service's price, and what the payment gateway itself takes is recorded instead.

-- The fee's general setting (V49) is no longer read
DELETE FROM nexhome_app_settings WHERE setting_key = 'TRANSACTION_FEE_PERCENTAGE';

-- The gateway's charges on a successful attempt (Cashfree: the PAYMENT_CHARGES_WEBHOOK, GET /orders/{id}/settlements)
-- and its settlement to NexHome's bank account
ALTER TABLE nexhome_payment_attempts
	ADD COLUMN gateway_charge      NUMERIC(12, 2),
	ADD COLUMN gateway_tax         NUMERIC(12, 2),
	ADD COLUMN settlement_amount   NUMERIC(12, 2),
	ADD COLUMN settlement_currency CHAR(3),
	ADD COLUMN settlement_id       VARCHAR(100),
	ADD COLUMN settlement_utr      VARCHAR(100),
	ADD COLUMN settled_at          TIMESTAMPTZ,
	ADD COLUMN charges_recorded_at TIMESTAMPTZ,
	ADD CONSTRAINT ck_nexhome_payment_attempts_charges CHECK (
		(gateway_charge IS NULL OR gateway_charge >= 0) AND (gateway_tax IS NULL OR gateway_tax >= 0)
		AND (charges_recorded_at IS NOT NULL OR (gateway_charge IS NULL AND gateway_tax IS NULL)));

-- Attempts whose charges or settlement are still to come (the hourly job asks the gateway)
CREATE INDEX ix_nexhome_payment_attempts_unsettled ON nexhome_payment_attempts (attempted_at)
	WHERE attempt_status = 'SUCCESS' AND settlement_id IS NULL;

-- Each booking's share of its order's charges (charge + tax), split by amount
ALTER TABLE nexhome_payments
	ADD COLUMN gateway_charges_amount NUMERIC(12, 2),
	ADD CONSTRAINT ck_nexhome_payments_gateway_charges CHECK (gateway_charges_amount IS NULL
		OR gateway_charges_amount >= 0);
