package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param activeFlag defaults to true when missing
 */
public record NexHomeRoleCreateRequest(
		@NotBlank(message = "Role code is required")
		@Pattern(regexp = "^\\s*[A-Z][A-Z0-9_]*\\s*$",
				message = "Role code must contain only capital letters, digits and underscores, starting with a letter")
		@Size(max = 30, message = "Role code must be at most 30 characters")
		String code,

		@NotBlank(message = "Role name is required")
		@Size(max = 80, message = "Role name must be at most 80 characters")
		String name,

		String description,

		Boolean activeFlag) {
}
