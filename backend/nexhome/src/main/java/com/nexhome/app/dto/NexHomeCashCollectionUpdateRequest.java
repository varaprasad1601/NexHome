package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * COLLECTED (the assigned partner or staff), DISPUTED or WAIVED (staff), with notes.
 */
public record NexHomeCashCollectionUpdateRequest(
		@NotNull(message = "Cash collection ID is required")
		Long cashCollectionId,

		@NotBlank(message = "Status is required")
		String status,

		@Size(max = 2000, message = "Notes must be at most 2000 characters")
		String notes) {
}
