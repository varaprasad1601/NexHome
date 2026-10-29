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
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant expiresAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
