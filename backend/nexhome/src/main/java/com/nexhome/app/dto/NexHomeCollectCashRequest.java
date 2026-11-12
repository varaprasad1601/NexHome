package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The partner collected cash for an online booking whose online payment did not go through: the booking becomes a
 * cash booking with its cash collected.
 */
public record NexHomeCollectCashRequest(
		@NotNull(message = "Booking ID is required")
		Long bookingId,
		@Size(max = 1000, message = "Notes must be at most 1000 characters")
		String notes) {
}
