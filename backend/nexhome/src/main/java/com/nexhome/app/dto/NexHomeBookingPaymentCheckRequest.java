package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/** Asks whether a booking's online payment has come in (the gateway is asked for its open order). */
public record NexHomeBookingPaymentCheckRequest(
		@NotNull(message = "Booking ID is required")
		Long bookingId) {
}
