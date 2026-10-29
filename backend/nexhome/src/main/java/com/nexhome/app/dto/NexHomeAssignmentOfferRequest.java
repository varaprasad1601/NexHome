package com.nexhome.app.dto;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Offers a booking to a partner, holding the partner's time until the offer runs out ({@code expiresInMinutes}, default
 * 30). {@code direct: true} assigns the partner at once, as if they had accepted. {@code outsideWeeklyHours: true}
 * reserves the partner even outside their weekly hours (a requested slot); time off and other bookings still block it.
 * {@code startsAt} / {@code endsAt} (together) allot the slot: they become the booking's scheduled start and end, e.g.
 * for an INSTANT booking, which has none.
 */
public record NexHomeAssignmentOfferRequest(
		@NotNull(message = "Booking ID is required")
		Long bookingId,

		@NotNull(message = "Partner profile ID is required")
		Long partnerProfileId,

		@Min(value = 1, message = "Expires in minutes must be between 1 and 60")
		@Max(value = 60, message = "Expires in minutes must be between 1 and 60")
		Integer expiresInMinutes,

		Boolean direct,

		Boolean outsideWeeklyHours,

		Instant startsAt,

		Instant endsAt) {
}
