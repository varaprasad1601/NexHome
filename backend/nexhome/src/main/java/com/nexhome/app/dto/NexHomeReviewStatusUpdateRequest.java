package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Staff (REVIEW_MANAGE write) publish, hide or hold a review: a REVIEW_STATUS code. */
public record NexHomeReviewStatusUpdateRequest(
		@NotNull(message = "Review ID is required")
		Long reviewId,
		@NotBlank(message = "Review status is required")
		String reviewStatus) {
}
