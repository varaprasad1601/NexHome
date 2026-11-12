package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/** One attempt to pay a payment order at the gateway (V67), as on the gateway's dashboard. */
public record NexHomePaymentAttemptResponse(
		Long attemptId,
		Long paymentOrderId,
		String provider,
		/** The gateway's payment id (Cashfree's cf_payment_id). */
		String providerPaymentId,
		/** SUCCESS, FAILED, PENDING, USER_DROPPED, CANCELLED, VOID, NOT_ATTEMPTED. */
		String attemptStatus,
		String paymentMode,
		String paymentMethodDetail,
		BigDecimal amount,
		String currency,
		String bankReference,
		String errorCode,
		String message,
		/** What the gateway charged the customer on top (V69: Cashfree's surcharge and its GST). */
		BigDecimal surchargeAmount,
		BigDecimal surchargeTax,
		/** The gateway's charge and its tax on a successful attempt (V68), null until known. */
		BigDecimal gatewayCharge,
		BigDecimal gatewayTax,
		/** What the gateway pays NexHome (amount less charge and tax), with its settlement id, UTR and time. */
		BigDecimal settlementAmount,
		String settlementCurrency,
		String settlementId,
		String settlementUtr,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant settledAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant chargesRecordedAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant attemptedAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant completedAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant updatedDate) {
}
