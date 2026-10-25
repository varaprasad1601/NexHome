package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

public record NexHomeUserRoleAssignRequest(
		@NotNull(message = "User ID is required")
		Long userId,

		@NotNull(message = "Role is required")
		Short roleId) {
}
