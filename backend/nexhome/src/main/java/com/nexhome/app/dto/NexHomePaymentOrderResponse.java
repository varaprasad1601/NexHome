package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomePaymentOrderResponse(
		Long paymentOrderId,
		String publicId,
		UUID checkoutId,
		Long customerProfileId,
		String customerName,
		String provider,
		String providerOrderId,
		BigDecimal amount,
		String currency,
		String paymentStatus,
		/** The token the gateway's checkout opens with (V66: Cashfree's payment_session_id), null for MANUAL. */
		String checkoutToken,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant expiresAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate,
		/**
		 * Only from POST /orders/verify: the latest payment attempt at the gateway (SUCCESS, FAILED, PENDING,
		 * USER_DROPPED, CANCELLED, ...; null without one or elsewhere), its message and its id (the gateway's payment
		 * id: a new attempt has a new one).
		 */
		String lastAttemptStatus,
		String lastAttemptMessage,
		String lastAttemptId,
		/** Only in GET /api/payments/orders: the online bookings of the order's checkout (null elsewhere). */
		java.util.List<OrderBooking> bookings) {

	/** A booking an order pays for: its id and public id (NHBOOK...). */
	public record OrderBooking(Long bookingId, String publicId) {
	}

	/** This order with the gateway's latest attempt. */
	public NexHomePaymentOrderResponse withAttempt(String status, String message, String attemptId) {
		return new NexHomePaymentOrderResponse(paymentOrderId, publicId, checkoutId, customerProfileId, customerName,
				provider, providerOrderId, amount, currency, paymentStatus, checkoutToken, expiresAt, creationDate,
				status, message, attemptId, bookings);
	}

	/** This order with the bookings it pays for. */
	public NexHomePaymentOrderResponse withBookings(java.util.List<OrderBooking> orderBookings) {
		return new NexHomePaymentOrderResponse(paymentOrderId, publicId, checkoutId, customerProfileId, customerName,
				provider, providerOrderId, amount, currency, paymentStatus, checkoutToken, expiresAt, creationDate,
				lastAttemptStatus, lastAttemptMessage, lastAttemptId, orderBookings);
	}
}
