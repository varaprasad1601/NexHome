package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomePayoutResponse(
		Long payoutId,
		String publicId,
		Long partnerProfileId,
		String partnerPublicId,
		String partnerName,
		Long bookingId,
		String bookingPublicId,
		Long paymentId,
		BigDecimal grossAmount,
		BigDecimal platformCommission,
		BigDecimal adjustmentsAmount,
		BigDecimal netAmount,
		String providerTransferId,
		String payoutStatus,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant scheduledAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant paidAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
