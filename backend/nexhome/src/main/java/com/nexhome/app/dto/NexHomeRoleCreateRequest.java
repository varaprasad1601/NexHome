package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param activeFlag defaults to true when missing
 */
public record NexHomeRoleCreateRequest(
		@NotBlank(message = "Role name is required")
		@Size(max = 50, message = "Role name must be at most 50 characters")
		String roleName,

		@Size(max = 500, message = "Description must be at most 500 characters")
		String description,

		Boolean activeFlag) {
}
