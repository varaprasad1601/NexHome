package com.nexhome.app.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The payment provider (Module 7): orders, refunds, transfers to partners and webhook signatures. The only one so far
 * is {@link NexHomeManualPaymentGateway} (provider MANUAL), a simulation: replace it with a real provider (e.g.
 * Razorpay) behind this interface once a gateway account exists. Never log or store card details or provider secrets.
 */
public interface NexHomePaymentGateway {

	/** The provider's name, stored on orders, payments and webhook events (≤ 30 characters). */
	String provider();

	/** Creates the gateway order; returns its id. The same idempotency key must give the same order. */
	String createOrder(String idempotencyKey, BigDecimal amount, String currency);

	/** Refunds part or all of a captured payment; returns the refund id. */
	String refund(String providerPaymentId, BigDecimal amount);

	/**
	 * Sends money to a partner's bank account; returns the provider's answer ({@code transferId}, {@code status}:
	 * SETTLED or FAILED, and anything else safe to keep). Retried with the same idempotency key.
	 */
	Map<String, Object> transfer(String idempotencyKey, BigDecimal amount, String accountReference);

	/** Whether {@code signature} is the provider's signature of the webhook body. */
	boolean verifySignature(String payload, String signature);
}
