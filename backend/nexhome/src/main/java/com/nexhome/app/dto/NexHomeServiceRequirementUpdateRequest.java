package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates a requirement. A null (or missing) field keeps its current value; the description is required and cannot be
 * blanked. The service is fixed: it is only declared here so that a request containing it is rejected with a clear
 * message.
 */
public record NexHomeServiceRequirementUpdateRequest(
		@NotNull(message = "Service requirement ID is required")
		Long serviceRequirementId,

		@Null(message = "Service cannot be updated")
		Long serviceId,

		String requirementType,

		@Pattern(regexp = "(?s).*\\S.*", message = "Description cannot be blank")
		@Size(max = 2000, message = "Description must be at most 2000 characters")
		String description,

		Boolean isMandatory,

		Integer displayOrder) {
}
