package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A payment to a partner recorded by staff (V62). {@code paidOn} is the day it was paid (yyyy-MM-dd);
 * {@code createdByName} who recorded it; {@code payoutStatus} RECORDED or CANCELLED (with when, who and why);
 * {@code deduction} the deduction recorded with it (V63), or null.
 */
public record NexHomeManualPayoutResponse(
		Long payoutId,
		String publicId,
		Long partnerProfileId,
		String partnerPublicId,
		String partnerName,
		BigDecimal amount,
		String paymentMode,
		String transactionId,
		LocalDate paidOn,
		String notes,
		String payoutStatus,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant cancelledAt,

		String cancelledByName,
		String cancellationReason,
		Long createdBy,
		String createdByName,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate,

		NexHomePartnerDeductionResponse deduction) {
}
