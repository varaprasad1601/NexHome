package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Staff withdraw an offer, or take an accepted booking back from its partner.
 */
public record NexHomeAssignmentCancelRequest(
		@NotNull(message = "Assignment ID is required")
		Long assignmentId,

		@Size(max = 2000, message = "Reason must be at most 2000 characters")
		String reason) {
}
