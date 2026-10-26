package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creates the profile of a staff account (one holding SUPER_USER, ADMIN or SUB_ADMIN) with its registered address.
 * The country defaults to IN, where the postal code must be a 6-digit PIN code.
 */
public record NexHomeAdminProfileCreateRequest(
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

		// Optional; stored upper-cased and unique when present
		@Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9_-]*$",
				message = "Employee code must contain only letters, digits, hyphens and underscores")
		@Size(max = 30, message = "Employee code must be at most 30 characters")
		String employeeCode,

		@Size(max = 100, message = "Department must be at most 100 characters")
		String department,

		@Size(max = 100, message = "Job title must be at most 100 characters")
		String jobTitle,

		@NotBlank(message = "Address line 1 is required")
		@Size(max = 255, message = "Address line 1 must be at most 255 characters")
		String addressLine1,

		@Size(max = 255, message = "Address line 2 must be at most 255 characters")
		String addressLine2,

		@Size(max = 255, message = "Landmark must be at most 255 characters")
		String landmark,

		@NotBlank(message = "City is required")
		@Size(max = 100, message = "City must be at most 100 characters")
		String city,

		@NotBlank(message = "State is required")
		@Size(max = 100, message = "State must be at most 100 characters")
		String state,

		@NotBlank(message = "Postal code is required")
		@Pattern(regexp = "^\\s*[A-Za-z0-9][A-Za-z0-9 -]{1,10}[A-Za-z0-9]\\s*$",
				message = "Postal code must be 3 to 12 letters, digits, spaces or hyphens")
		String postalCode,

		@Pattern(regexp = "^[A-Za-z]{2}$", message = "Country code must be a 2-letter ISO country code, e.g. IN")
		String countryCode) {
}
