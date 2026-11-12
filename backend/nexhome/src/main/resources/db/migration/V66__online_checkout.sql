-- Online payments through a gateway (Cashfree): an order keeps the token its checkout opens with (Cashfree's
-- payment_session_id; null for MANUAL), and a payment how it was paid (the gateway's payment group: upi, credit_card,
-- debit_card, net_banking, wallet, ...).
ALTER TABLE nexhome_payment_orders
	ADD COLUMN checkout_token TEXT;

ALTER TABLE nexhome_payments
	ADD COLUMN payment_mode VARCHAR(40);
