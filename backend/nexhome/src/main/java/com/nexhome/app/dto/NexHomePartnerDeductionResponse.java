package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * An amount NexHome kept from what it owed a partner (V63): {@code deductionReason} DEDUCTION_REASON, {@code notes},
 * {@code deductedOn} (yyyy-MM-dd), the payout it was recorded with ({@code payoutId} / {@code payoutPublicId}, or
 * null), {@code status} RECORDED or CANCELLED (with when, who and why).
 */
public record NexHomePartnerDeductionResponse(
		Long deductionId,
		String publicId,
		Long partnerProfileId,
		String partnerPublicId,
		String partnerName,
		Long payoutId,
		String payoutPublicId,
		BigDecimal amount,
		String deductionReason,
		String notes,
		LocalDate deductedOn,
		String status,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant cancelledAt,

		String cancelledByName,
		String cancellationReason,
		Long createdBy,
		String createdByName,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
