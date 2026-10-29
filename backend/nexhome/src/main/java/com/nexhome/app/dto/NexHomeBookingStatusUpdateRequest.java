package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Staff: PENDING to CONFIRMED (cash bookings; online ones are confirmed by their payment, or by hand), ASSIGNED to
 * PARTNER_EN_ROUTE. The assigned partner: ASSIGNED to PARTNER_EN_ROUTE. The timer moves the rest; cancelling has its own request.
 */
public record NexHomeBookingStatusUpdateRequest(
		@NotNull(message = "Booking ID is required")
		Long bookingId,

		@NotBlank(message = "Booking status is required")
		String bookingStatus,

		@Size(max = 2000, message = "Reason must be at most 2000 characters")
		String reason) {
}
