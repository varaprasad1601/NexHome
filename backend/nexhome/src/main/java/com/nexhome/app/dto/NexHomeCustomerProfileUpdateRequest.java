package com.nexhome.app.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates a customer profile. A null (or missing) field keeps its current value; names cannot be blanked, and an
 * empty preferred language clears it. The owner and public id are fixed: they are only declared here so that a
 * request containing them is rejected with a clear message.
 */
public record NexHomeCustomerProfileUpdateRequest(
		@NotNull(message = "Customer profile ID is required")
		Long customerProfileId,

		@Null(message = "User cannot be updated")
		Long userId,

		@Null(message = "Public ID cannot be updated")
		String publicId,

		@Pattern(regexp = "^\\s*[\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]*$",
				message = "First name must contain only letters, spaces, dots, apostrophes and hyphens")
		@Size(max = 100, message = "First name must be at most 100 characters")
		String firstName,

		@Pattern(regexp = "^\\s*[\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]*$",
				message = "Last name must contain only letters, spaces, dots, apostrophes and hyphens")
		@Size(max = 100, message = "Last name must be at most 100 characters")
		String lastName,

		@Past(message = "Date of birth must be in the past")
		LocalDate dateOfBirth,

		String preferredLanguage,

		Boolean marketingConsent) {
}
