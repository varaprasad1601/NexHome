package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Sets an approved partner ONLINE, OFFLINE or BUSY (PARTNER_AVAILABILITY_STATUS code). */
public record NexHomePartnerAvailabilityUpdateRequest(
		@NotNull(message = "Partner profile ID is required")
		Long partnerProfileId,

		@NotBlank(message = "Availability status is required")
		String availabilityStatus) {
}
