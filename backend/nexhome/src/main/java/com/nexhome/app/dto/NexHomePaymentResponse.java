package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A payment of a booking, with what has been refunded from it.
 */
public record NexHomePaymentResponse(
		Long paymentId,
		Long paymentOrderId,
		String paymentOrderPublicId,
		Long bookingId,
		String bookingPublicId,
		Long customerProfileId,
		String customerName,
		/** The booking's total (what the customer owes for it); {@code amount} is what this payment captured. */
		BigDecimal bookingTotalAmount,
		String provider,
		String providerPaymentId,
		String paymentMethod,
		/** How it was paid (V66): the gateway's payment group, e.g. upi, credit_card; null for MANUAL. */
		String paymentMode,
		BigDecimal amount,
		BigDecimal refundedAmount,
		/** This booking's share of the gateway's charges (charge + tax, V68); null until the gateway reports them. */
		BigDecimal gatewayChargesAmount,
		/** What the customer paid the gateway on top for this booking (V69: surcharge + GST); null without. */
		BigDecimal customerChargesAmount,
		String currency,
		String status,
		String failureCode,
		String failureMessage,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant capturedAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
