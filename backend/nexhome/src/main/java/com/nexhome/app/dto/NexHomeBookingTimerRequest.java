package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A service timer event: START, PAUSE, RESUME or STOP (BOOKING_TIMER_EVENT).
 */
public record NexHomeBookingTimerRequest(
		@NotNull(message = "Booking ID is required")
		Long bookingId,

		@NotBlank(message = "Event type is required")
		String eventType,

		@Size(max = 2000, message = "Notes must be at most 2000 characters")
		String notes,

		/** START by the partner: the customer's 4-digit start code (V59). Staff starting the work leave it out. */
		@Size(max = 10, message = "Start code must be at most 10 characters")
		String otp) {
}
