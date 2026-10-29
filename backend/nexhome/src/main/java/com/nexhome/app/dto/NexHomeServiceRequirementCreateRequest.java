package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Adds a free-text prerequisite or instruction to a service, for the CUSTOMER (default) or the PARTNER.
 */
public record NexHomeServiceRequirementCreateRequest(
		@NotNull(message = "Service is required")
		Long serviceId,

		@Pattern(regexp = "^(CUSTOMER|PARTNER)$", message = "Requirement for must be CUSTOMER or PARTNER")
		String requirementFor,

		@NotBlank(message = "Requirement type is required")
		String requirementType,

		@NotBlank(message = "Description is required")
		@Size(max = 2000, message = "Description must be at most 2000 characters")
		String description,

		Boolean isMandatory,

		Integer displayOrder) {
}
