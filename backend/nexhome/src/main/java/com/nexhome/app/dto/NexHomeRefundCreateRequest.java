package com.nexhome.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Refunds part or all of a captured payment.
 */
public record NexHomeRefundCreateRequest(
		@NotNull(message = "Payment ID is required")
		Long paymentId,

		@NotNull(message = "Amount is required")
		@DecimalMin(value = "0.01", message = "Amount must be more than 0")
		@Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimals")
		BigDecimal amount,

		@NotBlank(message = "Reason is required")
		@Size(max = 2000, message = "Reason must be at most 2000 characters")
		String reason) {
}
