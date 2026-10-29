package com.nexhome.app.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Changes a period of time off; a null field keeps its value, a reason of "" clears it. */
public record NexHomePartnerTimeOffUpdateRequest(
		@NotNull(message = "Time off ID is required")
		Long timeOffId,

		Instant startsAt,

		Instant endsAt,

		@Size(max = 200, message = "Reason must be at most 200 characters")
		String reason) {
}
