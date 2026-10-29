package com.nexhome.app.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A period the partner does not work. Times are ISO-8601 instants, e.g. {@code 2026-11-02T03:30:00Z}. */
public record NexHomePartnerTimeOffCreateRequest(
		@NotNull(message = "Partner profile ID is required")
		Long partnerProfileId,

		@NotNull(message = "Start is required")
		Instant startsAt,

		@NotNull(message = "End is required")
		Instant endsAt,

		@Size(max = 200, message = "Reason must be at most 200 characters")
		String reason) {
}
