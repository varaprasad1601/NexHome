package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/** The partner's Finish work on an online job: the customer is asked to confirm the work is done (V70). */
public record NexHomeBookingCompletionRequest(
		@NotNull(message = "Booking ID is required")
		Long bookingId) {
}
