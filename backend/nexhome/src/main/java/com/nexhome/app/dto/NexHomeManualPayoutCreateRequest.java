package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A payment made to a partner outside the app (V62): {@code paymentMode} PAYOUT_MODE (PHONEPE, GOOGLE_PAY, PAYTM,
 * OTHER_UPI, BANK_TRANSFER, CASH), {@code transactionId} (required except for CASH), {@code paidOn} (not in the
 * future), {@code amount} (at most what NexHome owes the partner). With {@code deductionAmount} (V63) a deduction is
 * recorded with it, linked to the payout, the same day: {@code deductionReason} (DEDUCTION_REASON) and
 * {@code deductionNotes} (required for OTHER); payment + deduction at most what is owed.
 */
public record NexHomeManualPayoutCreateRequest(
		@NotNull(message = "Partner is required")
		Long partnerProfileId,

		@NotNull(message = "Amount is required")
		@DecimalMin(value = "0.01", message = "Amount must be more than 0")
		@Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimals")
		BigDecimal amount,

		@NotBlank(message = "Paid through is required")
		String paymentMode,

		@Size(max = 100, message = "Transaction ID must be at most 100 characters")
		String transactionId,

		@NotNull(message = "Payment date is required")
		LocalDate paidOn,

		@Size(max = 500, message = "Notes must be at most 500 characters")
		String notes,

		@DecimalMin(value = "0.01", message = "Deduction amount must be more than 0")
		@Digits(integer = 10, fraction = 2, message = "Deduction amount must have at most 2 decimals")
		BigDecimal deductionAmount,

		String deductionReason,

		@Size(max = 500, message = "Deduction notes must be at most 500 characters")
		String deductionNotes) {
}
