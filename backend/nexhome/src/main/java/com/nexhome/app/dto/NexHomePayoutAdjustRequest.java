package com.nexhome.app.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Sets a pending payout's adjustments (a bonus is positive, a deduction negative); the net is worked out again and
 * must not go below 0.
 */
public record NexHomePayoutAdjustRequest(
		@NotNull(message = "Payout ID is required")
		Long payoutId,

		@NotNull(message = "Adjustments amount is required")
		@Digits(integer = 10, fraction = 2, message = "Adjustments amount must have at most 2 decimals")
		BigDecimal adjustmentsAmount) {
}
