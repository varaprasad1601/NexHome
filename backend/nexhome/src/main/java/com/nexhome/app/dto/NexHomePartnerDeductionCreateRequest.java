package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A deduction on its own (V63): {@code deductionReason} DEDUCTION_REASON, {@code notes} (required for OTHER),
 * {@code deductedOn} (not in the future), {@code amount} (at most what NexHome owes the partner).
 */
public record NexHomePartnerDeductionCreateRequest(
		@NotNull(message = "Partner is required")
		Long partnerProfileId,

		@NotNull(message = "Deduction amount is required")
		@DecimalMin(value = "0.01", message = "Deduction amount must be more than 0")
		@Digits(integer = 10, fraction = 2, message = "Deduction amount must have at most 2 decimals")
		BigDecimal amount,

		@NotBlank(message = "Deduction reason is required")
		String deductionReason,

		@Size(max = 500, message = "Deduction notes must be at most 500 characters")
		String notes,

		@NotNull(message = "Deduction date is required")
		LocalDate deductedOn) {
}
