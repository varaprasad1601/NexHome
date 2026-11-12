package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cancels a payout recorded by mistake (it no longer counts as paid). */
public record NexHomeManualPayoutCancelRequest(
		@NotNull(message = "Payout ID is required")
		Long payoutId,

		@NotBlank(message = "Reason is required")
		@Size(max = 200, message = "Reason must be at most 200 characters")
		String reason) {
}
