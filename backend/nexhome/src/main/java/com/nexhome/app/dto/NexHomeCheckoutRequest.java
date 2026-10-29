package com.nexhome.app.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Books every item of the cart for one address and time. {@code scheduledStartAt} (ISO-8601 instant) is required
 * for SCHEDULED; INSTANT bookings start half an hour from now.
 */
public record NexHomeCheckoutRequest(
		@NotNull(message = "Address ID is required")
		Long addressId,

		@NotBlank(message = "Booking type is required")
		String bookingType,

		Instant scheduledStartAt,

		@NotBlank(message = "Payment method is required")
		String paymentMethod) {
}
