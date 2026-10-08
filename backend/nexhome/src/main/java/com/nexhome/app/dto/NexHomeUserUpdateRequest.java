package com.nexhome.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

/**
 * Updates a user's email and active flag; a null (or missing) field keeps its current value and an empty email clears
 * it. Phone and role identify the user and cannot be changed, and the password has its own endpoint
 * (PUT /api/users/password/update, which checks the current password). They are only declared here so that a
 * request containing them is rejected with a clear message instead of being silently ignored.
 */
public record NexHomeUserUpdateRequest(
		@NotNull(message = "User ID is required")
		Long userId,

        @Null(message = "Phone cannot be updated")
        String phone,

        @Null(message = "Role cannot be updated")
        Long roleId,

		@Email(regexp = "^$|^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "Email must be a valid email address")
		@Size(max = 254, message = "Email must be at most 254 characters")
		String email,

		Boolean activeFlag,

		// Passwords have their own endpoints (POST /api/users/password/set, PUT /api/users/password/update);
		// declared only so that sending it here is rejected instead of silently ignored
		@Null(message = "Password cannot be updated here")
		String password) {
}
