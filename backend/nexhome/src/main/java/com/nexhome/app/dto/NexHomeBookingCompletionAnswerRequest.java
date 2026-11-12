package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/** The customer's answer (V70): is the work done? */
public record NexHomeBookingCompletionAnswerRequest(
		@NotNull(message = "Booking ID is required")
		Long bookingId,
		@NotNull(message = "Completed is required")
		Boolean completed) {
}
