package com.nexhome.app.service;

import java.util.List;

import java.math.BigDecimal;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * The payment provider (Module 7): orders the customer pays through, their status, refunds, transfers to partners and
 * webhooks. {@code nexhome.payments.provider} picks one: {@link NexHomeManualPaymentGateway} (MANUAL, a simulation:
 * staff record payments; the default) or {@link NexHomeCashfreePaymentGateway} (CASHFREE: the customer pays in the
 * app through Cashfree's checkout). Never log or store card details or provider secrets.
 */
public interface NexHomePaymentGateway {

	/** Who pays: the gateway's customer record. {@code phone} is the 10-digit mobile number. */
	record Customer(String customerId, String phone, String name, String email) {
	}

	/** A created order: the provider's order id and the token its checkout opens with (null: no online checkout). */
	record Order(String providerOrderId, String checkoutToken) {
	}

	/**
	 * What the provider says of an order: {@code status} PAID (then the successful payment's id and mode, e.g. upi),
	 * PENDING (not paid yet; the customer may still pay or retry) or EXPIRED (it can no longer be paid);
	 * {@code attemptStatus}: the latest payment attempt's outcome (SUCCESS, FAILED, PENDING, USER_DROPPED,
	 * CANCELLED, ...; null without one) and {@code attemptId} (its provider payment id), so the app can close the
	 * payment page once the customer has tried again (an attempt made before the page opened does not count);
	 * {@code attempts}: every attempt the provider lists (V67, kept in nexhome_payment_attempts).
	 */
	record OrderStatus(String status, String providerPaymentId, String paymentMode, String failureMessage,
			String attemptStatus, String attemptId, List<Attempt> attempts) {

		public OrderStatus(String status, String providerPaymentId, String paymentMode, String failureMessage) {
			this(status, providerPaymentId, paymentMode, failureMessage, null, null, List.of());
		}

		public static final String PAID = "PAID";
		public static final String PENDING = "PENDING";
		public static final String EXPIRED = "EXPIRED";
	}

	/**
	 * One attempt to pay an order, as the provider reports it: its id, status (SUCCESS, FAILED, PENDING,
	 * USER_DROPPED, CANCELLED, VOID, NOT_ATTEMPTED), payment group and what was used (UPI id, card, bank), amount,
	 * the bank's reference, error code and message, and when it was made and finished.
	 */
	record Attempt(String providerPaymentId, String status, String paymentMode, String methodDetail,
			java.math.BigDecimal amount, String currency, String bankReference, String errorCode, String message,
			java.time.Instant attemptedAt, java.time.Instant completedAt, java.math.BigDecimal surcharge,
			java.math.BigDecimal surchargeTax) {

		/** Without a surcharge (the customer paid nothing on top, or the gateway does not say). */
		public Attempt(String providerPaymentId, String status, String paymentMode, String methodDetail,
				java.math.BigDecimal amount, String currency, String bankReference, String errorCode, String message,
				java.time.Instant attemptedAt, java.time.Instant completedAt) {
			this(providerPaymentId, status, paymentMode, methodDetail, amount, currency, bankReference, errorCode,
					message, attemptedAt, completedAt, null, null);
		}
	}

	/**
	 * A webhook in the application's terms: {@code type} payment.captured, payment.failed, payment.charges (with
	 * {@code charges}) or the provider's own type (then ignored); {@code attempt}: the payment as an attempt, when the
	 * webhook carries it (kept like the order's attempts, with any surcharge the customer paid); {@code eventId} unique per provider (a repeat is answered as the first time).
	 */
	record WebhookEvent(String eventId, String type, String providerOrderId, String providerPaymentId,
			String paymentMode, String errorCode, String errorMessage, Charges charges, Attempt attempt) {

		public WebhookEvent(String eventId, String type, String providerOrderId, String providerPaymentId,
				String paymentMode, String errorCode, String errorMessage) {
			this(eventId, type, providerOrderId, providerPaymentId, paymentMode, errorCode, errorMessage, null, null);
		}
	}

	/**
	 * What the gateway took for a successful payment (V68): its charge and the tax on it, what it pays NexHome
	 * ({@code settlementAmount}) and, once paid out, the settlement's id, bank UTR and time. Fields the gateway has
	 * not reported yet are null.
	 */
	record Charges(String providerPaymentId, BigDecimal serviceCharge, BigDecimal serviceTax,
			BigDecimal settlementAmount, String settlementCurrency, String settlementId, String settlementUtr,
			java.time.Instant settledAt) {
	}

	/** The provider's name, stored on orders, payments and webhook events (≤ 30 characters). */
	String provider();

	/**
	 * Creates the gateway order for {@code reference} (letters, digits, _ and -, at most 45; the same reference gives
	 * the same order).
	 */
	Order createOrder(String reference, BigDecimal amount, String currency, Customer customer);

	/** Asks the provider about an order; null when it cannot tell (MANUAL: staff record payments). */
	OrderStatus fetchOrder(String providerOrderId);

	/**
	 * The gateway's charges and settlement of a paid order; null while it has none to give (not settled yet, or
	 * MANUAL, which charges nothing). Never throws for an order not settled yet.
	 */
	default Charges fetchCharges(String providerOrderId) {
		return null;
	}

	/**
	 * Ends an unpaid order at the gateway so it can no longer be paid (the partner collected cash instead). MANUAL
	 * has nothing to end.
	 */
	default void cancelOrder(String providerOrderId) {
	}

	/** Refunds part or all of a captured payment; {@code reference} is ours (letters and digits). Returns the refund id. */
	String refund(String providerOrderId, String providerPaymentId, String reference, BigDecimal amount);

	/**
	 * Sends money to a partner's bank account; returns the provider's answer ({@code transferId}, {@code status}:
	 * SETTLED or FAILED, and anything else safe to keep). Retried with the same idempotency key.
	 */
	Map<String, Object> transfer(String idempotencyKey, BigDecimal amount, String accountReference);

	/** Whether the webhook is the provider's: its signature over the raw body; {@code headers} lower-cased names. */
	boolean verifyWebhook(String payload, Map<String, String> headers);

	/** Reads a webhook body; an event without what is needed comes back with nulls. */
	WebhookEvent parseWebhook(JsonNode body);

	/** The provider's checkout mode for the hosted payment page (sandbox / production), or null without one. */
	String checkoutMode();
}
