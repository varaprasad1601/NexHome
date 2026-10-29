package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The partner (or staff for them) accepts or rejects an offer; a rejection needs a reason.
 */
public record NexHomeAssignmentRespondRequest(
		@NotNull(message = "Assignment ID is required")
		Long assignmentId,

		@NotNull(message = "Accept is required")
		Boolean accept,

		@Size(max = 2000, message = "Rejection reason must be at most 2000 characters")
		String rejectionReason) {
}
