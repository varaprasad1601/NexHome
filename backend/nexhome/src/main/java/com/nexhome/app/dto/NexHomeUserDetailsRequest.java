package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NexHomeUserDetailsRequest(
		@NotNull(message = "User is required")
		Long userId,

		@NotBlank(message = "First name is required")
		@Size(max = 100, message = "First name must be at most 100 characters")
		String firstName,

		@Size(max = 100, message = "Last name must be at most 100 characters")
		String lastName) {
}
