package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cancels time off: the partner or PARTNER_AVAILABILITY_MANAGE write. Upcoming time off is cancelled; time off already
 * under way ends now (cut short). Over or already cancelled time off cannot be cancelled.
 */
public record NexHomePartnerTimeOffCancelRequest(
		@NotNull(message = "Time off ID is required")
		Long timeOffId,
		@Size(max = 200, message = "Reason must be at most 200 characters")
		String reason) {
}
