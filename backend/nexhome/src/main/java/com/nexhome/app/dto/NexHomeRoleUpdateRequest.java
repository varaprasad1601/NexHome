package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

/**
 * Updates a role's description and active flag. The optional description is always replaced (null, missing or empty
 * clears it); a null activeFlag is unchanged. The role name identifies the role and cannot be changed: it is only
 * declared here so that a request containing it is rejected with a clear message instead of being silently ignored.
 */
public record NexHomeRoleUpdateRequest(
		@NotNull(message = "Role ID is required")
		Long roleId,

		@Null(message = "Role name cannot be updated")
		String roleName,

		@Size(max = 500, message = "Description must be at most 500 characters")
		String description,

		Boolean activeFlag) {
}
