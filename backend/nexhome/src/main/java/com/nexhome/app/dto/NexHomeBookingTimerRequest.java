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
		String notes) {
}
