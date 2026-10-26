package com.nexhome.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

/**
 * Updates a partner service. A null (or missing) field keeps its current value. {@code isPrimaryService: true} makes
 * it the primary service (replacing the current one); {@code false} leaves the partner without one. The partner and
 * service are fixed: they are only declared here so that a request containing them is rejected with a clear message.
 */
public record NexHomePartnerServiceUpdateRequest(
		@NotNull(message = "Partner service ID is required")
		Long partnerServiceId,

		@Null(message = "Partner profile cannot be updated")
		Long partnerProfileId,

		@Null(message = "Service cannot be updated")
		Long serviceId,

		@Min(value = 0, message = "Experience years must be between 0 and 60")
		@Max(value = 60, message = "Experience years must be between 0 and 60")
		Integer experienceYears,

		Boolean isPrimaryService,

		Boolean isActive) {
}
