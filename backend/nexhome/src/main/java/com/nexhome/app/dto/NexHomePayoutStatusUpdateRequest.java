package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * PENDING to ON_HOLD or CANCELLED; ON_HOLD to PENDING or CANCELLED; FAILED to PENDING or CANCELLED.
 */
public record NexHomePayoutStatusUpdateRequest(
		@NotNull(message = "Payout ID is required")
		Long payoutId,

		@NotBlank(message = "Payout status is required")
		String payoutStatus) {
}
