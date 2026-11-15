package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/** The partner reached the work area (V75), before starting the work: the customer is told. */
public record NexHomeBookingArrivedRequest(
		@NotNull(message = "Booking ID is required")
		Long bookingId) {
}
