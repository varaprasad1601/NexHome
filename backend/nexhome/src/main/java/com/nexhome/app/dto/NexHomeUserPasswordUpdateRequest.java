package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Changes a user's password. The current password must be given and must match.
 */
public record NexHomeUserPasswordUpdateRequest(
		@NotNull(message = "User ID is required")
		Long userId,

		@NotBlank(message = "Current password is required")
		String currentPassword,

		// BCrypt only uses the first 72 bytes; same rule as user creation
		@NotBlank(message = "New password is required")
		@Size(min = 8, max = 16, message = "New password must be between 8 and 16 characters")
		String newPassword) {
}
