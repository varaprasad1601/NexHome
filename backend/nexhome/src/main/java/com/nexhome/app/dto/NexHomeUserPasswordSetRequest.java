package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * First password of an approved user. Setting it activates the account.
 */
public record NexHomeUserPasswordSetRequest(
		@NotNull(message = "User ID is required")
		Long userId,

		// BCrypt only uses the first 72 bytes; same rule as the password update
		@NotBlank(message = "Password is required")
		@Size(min = 8, max = 16, message = "Password must be between 8 and 16 characters")
		String password,

		@NotBlank(message = "Confirm password is required")
		String confirmPassword) {
}
