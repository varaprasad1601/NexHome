package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Adds a customer prerequisite or instruction to a service.
 */
public record NexHomeServiceRequirementCreateRequest(
		@NotNull(message = "Service is required")
		Long serviceId,

		@NotBlank(message = "Requirement type is required")
		String requirementType,

		@NotBlank(message = "Description is required")
		@Size(max = 2000, message = "Description must be at most 2000 characters")
		String description,

		Boolean isMandatory,

		Integer displayOrder) {
}
