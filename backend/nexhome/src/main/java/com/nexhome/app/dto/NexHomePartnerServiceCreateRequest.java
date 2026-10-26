package com.nexhome.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Adds an active service a partner offers. The first one becomes the primary service; {@code isPrimaryService: true}
 * makes a later one primary instead.
 */
public record NexHomePartnerServiceCreateRequest(
		@NotNull(message = "Partner profile ID is required")
		Long partnerProfileId,

		@NotNull(message = "Service is required")
		Long serviceId,

		@Min(value = 0, message = "Experience years must be between 0 and 60")
		@Max(value = 60, message = "Experience years must be between 0 and 60")
		Integer experienceYears,

		Boolean isPrimaryService) {
}
