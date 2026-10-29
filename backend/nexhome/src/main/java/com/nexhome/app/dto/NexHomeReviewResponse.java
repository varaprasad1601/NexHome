package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A review with its booking, customer and partner (names null until their profiles have them). {@code serviceName}
 * is the booking's service as booked.
 */
public record NexHomeReviewResponse(
		Long reviewId,
		Long bookingId,
		String bookingPublicId,
		String serviceName,
		Long customerProfileId,
		String customerPublicId,
		String customerName,
		Long partnerProfileId,
		String partnerPublicId,
		String partnerName,
		int serviceRating,
		int partnerRating,
		String reviewText,
		String reviewStatus,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate,
		Long createdBy,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant updatedDate,
		Long updatedBy) {
}
