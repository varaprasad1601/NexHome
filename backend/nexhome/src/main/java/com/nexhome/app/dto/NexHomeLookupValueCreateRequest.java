package com.nexhome.app.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param displayOrder defaults to 0 when missing
 * @param activeFlag defaults to true when missing
 * @param isDefault defaults to false; true makes this the type's only default value
 * @param metadata any JSON object; defaults to {}
 */
public record NexHomeLookupValueCreateRequest(
		@NotNull(message = "Lookup type ID is required")
		Long lookupTypeId,

		// Mostly capitals (e.g. IN_PROGRESS), but some codes are lower case, e.g. language codes such as "te"
		@NotBlank(message = "Value code is required")
		@Pattern(regexp = "^\\s*[A-Za-z0-9][A-Za-z0-9_.-]*\\s*$",
				message = "Value code must contain only letters, digits, underscores, dots and hyphens")
		@Size(max = 80, message = "Value code must be at most 80 characters")
		String valueCode,

		@NotBlank(message = "Display name is required")
		@Size(max = 150, message = "Display name must be at most 150 characters")
		String displayName,

		String description,

		Integer displayOrder,

		Boolean activeFlag,

		Boolean isDefault,

		Map<String, Object> metadata) {
}
