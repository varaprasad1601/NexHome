package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * A payment order for the online bookings of the booking's checkout that are not paid yet.
 */
public record NexHomePaymentOrderCreateRequest(
		@NotNull(message = "Booking ID is required")
		Long bookingId) {
}
