package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates service media. A null (or missing) field keeps its current value; the storage key cannot be blanked. The
 * service is fixed: it is only declared here so that a request containing it is rejected with a clear message.
 */
public record NexHomeServiceMediaUpdateRequest(
		@NotNull(message = "Service media ID is required")
		Long serviceMediaId,

		@Null(message = "Service cannot be updated")
		Long serviceId,

		String mediaType,

		@Pattern(regexp = "(?s).*\\S.*", message = "Storage key cannot be blank")
		@Size(max = 1024, message = "Storage key must be at most 1024 characters")
		String storageKey,

		Integer displayOrder,

		Boolean isActive) {
}
