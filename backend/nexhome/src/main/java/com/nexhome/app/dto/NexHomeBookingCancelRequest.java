package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NexHomeBookingCancelRequest(
		@NotNull(message = "Booking ID is required")
		Long bookingId,

		@NotBlank(message = "Cancellation reason is required")
		@Size(max = 2000, message = "Cancellation reason must be at most 2000 characters")
		String reason) {
}
