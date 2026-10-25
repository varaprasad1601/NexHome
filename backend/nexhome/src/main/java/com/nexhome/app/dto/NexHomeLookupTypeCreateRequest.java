package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param activeFlag defaults to true when missing
 * @param isSystemManaged defaults to false when missing: types created through the API are managed by admins
 */
public record NexHomeLookupTypeCreateRequest(
		@NotBlank(message = "Type code is required")
		@Pattern(regexp = "^\\s*[A-Z][A-Z0-9_]*\\s*$",
				message = "Type code must contain only capital letters, digits and underscores, starting with a letter")
		@Size(max = 80, message = "Type code must be at most 80 characters")
		String typeCode,

		@NotBlank(message = "Type name is required")
		@Size(max = 120, message = "Type name must be at most 120 characters")
		String typeName,

		String description,

		Boolean activeFlag,

		Boolean isSystemManaged) {
}
