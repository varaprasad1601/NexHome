package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Sends a payout to the partner's primary bank account.
 */
public record NexHomePayoutPayRequest(
		@NotNull(message = "Payout ID is required")
		Long payoutId) {
}
