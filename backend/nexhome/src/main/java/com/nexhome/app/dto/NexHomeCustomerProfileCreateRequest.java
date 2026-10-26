package com.nexhome.app.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creates the customer profile of an account that holds the CUSTOMER role. Names may use any script; surrounding
 * and repeated whitespace is removed.
 */
public record NexHomeCustomerProfileCreateRequest(
		@NotNull(message = "User ID is required")
		Long userId,

		@NotBlank(message = "First name is required")
		@Pattern(regexp = "^\\s*[\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]*$",
				message = "First name must contain only letters, spaces, dots, apostrophes and hyphens")
		@Size(max = 100, message = "First name must be at most 100 characters")
		String firstName,

		@NotBlank(message = "Last name is required")
		@Pattern(regexp = "^\\s*[\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]*$",
				message = "Last name must contain only letters, spaces, dots, apostrophes and hyphens")
		@Size(max = 100, message = "Last name must be at most 100 characters")
		String lastName,

		@Past(message = "Date of birth must be in the past")
		LocalDate dateOfBirth,

		// PREFERRED_CONTACT_LANGUAGE code, e.g. te
		String preferredLanguage,

		Boolean marketingConsent) {
}
