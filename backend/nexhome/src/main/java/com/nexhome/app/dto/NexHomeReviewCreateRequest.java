package com.nexhome.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A customer's review of a completed booking: the booking's customer, or staff with REVIEW_MANAGE write recording it
 * for them. One per booking.
 */
public record NexHomeReviewCreateRequest(
		@NotNull(message = "Booking ID is required")
		Long bookingId,
		@NotNull(message = "Service rating is required")
		@Min(value = 1, message = "Service rating must be between 1 and 5")
		@Max(value = 5, message = "Service rating must be between 1 and 5")
		Integer serviceRating,
		@NotNull(message = "Partner rating is required")
		@Min(value = 1, message = "Partner rating must be between 1 and 5")
		@Max(value = 5, message = "Partner rating must be between 1 and 5")
		Integer partnerRating,
		@Size(max = 2000, message = "Review text must be at most 2000 characters")
		String reviewText) {
}
