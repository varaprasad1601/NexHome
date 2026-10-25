package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Activates or withdraws a role the user already has; the user and role identify the assignment.
 */
public record NexHomeUserRoleUpdateRequest(
		@NotNull(message = "User ID is required")
		Long userId,

		@NotNull(message = "Role is required")
		Short roleId,

		@NotNull(message = "Active flag is required")
		Boolean isActive) {
}
