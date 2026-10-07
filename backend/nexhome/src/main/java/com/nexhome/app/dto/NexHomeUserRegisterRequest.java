package com.nexhome.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NexHomeUserRegisterRequest(
		@NotBlank(message = "Phone is required")
		@Pattern(regexp = "^[0-9]{10}$", message = "Phone must be exactly 10 digits")
		String phone,

		// Optional; when given, it needs a domain with a dot (plain @Email accepts "name@host")
		@Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "Email must be a valid email address")
		@Size(max = 254, message = "Email must be at most 254 characters")
		String email,

		@NotBlank(message = "Password is required")
		@Size(min = 8, max = 16, message = "Password must be between 8 and 16 characters")
		String password,

		@NotNull(message = "Role is required")
		Long roleId) {
}
