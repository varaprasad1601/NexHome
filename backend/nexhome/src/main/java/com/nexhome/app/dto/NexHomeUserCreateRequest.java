package com.nexhome.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NexHomeUserCreateRequest(
		@NotBlank(message = "Phone is required")
		@Pattern(regexp = "^[0-9]{10}$", message = "Phone must be exactly 10 digits")
		String phone,

		// Optional; when given, it needs a domain with a dot (plain @Email accepts "name@host")
		@Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "Email must be a valid email address")
		@Size(max = 254, message = "Email must be at most 254 characters")
		String email,

		@NotNull(message = "Role is required")
		Long roleId,

		// The password is set by the user after approval (POST /api/users/password/set); declared only so that
		// sending it here is rejected instead of silently ignored
		@Null(message = "Password cannot be set when creating a user")
		String password) {
}
