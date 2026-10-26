package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates a staff profile. A null (or missing) field keeps its current value; required fields cannot be blanked, and
 * an empty employee code, department, job title, address line 2 or landmark clears it. Staff may update their own
 * names and address; the employee code, department and job title need STAFF_MANAGE write. The owner and public id
 * are fixed: they are only declared here so that a request containing them is rejected with a clear message.
 */
public record NexHomeAdminProfileUpdateRequest(
		@NotNull(message = "Admin profile ID is required")
		Long adminProfileId,

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

		@Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9_-]*$",
				message = "Employee code must contain only letters, digits, hyphens and underscores")
		@Size(max = 30, message = "Employee code must be at most 30 characters")
		String employeeCode,

		@Size(max = 100, message = "Department must be at most 100 characters")
		String department,

		@Size(max = 100, message = "Job title must be at most 100 characters")
		String jobTitle,

		@Pattern(regexp = "(?s).*\\S.*", message = "Address line 1 cannot be blank")
		@Size(max = 255, message = "Address line 1 must be at most 255 characters")
		String addressLine1,

		@Size(max = 255, message = "Address line 2 must be at most 255 characters")
		String addressLine2,

		@Size(max = 255, message = "Landmark must be at most 255 characters")
		String landmark,

		@Pattern(regexp = "(?s).*\\S.*", message = "City cannot be blank")
		@Size(max = 100, message = "City must be at most 100 characters")
		String city,

		@Pattern(regexp = "(?s).*\\S.*", message = "State cannot be blank")
		@Size(max = 100, message = "State must be at most 100 characters")
		String state,

		@Pattern(regexp = "^\\s*[A-Za-z0-9][A-Za-z0-9 -]{1,10}[A-Za-z0-9]\\s*$",
				message = "Postal code must be 3 to 12 letters, digits, spaces or hyphens")
		String postalCode,

		@Pattern(regexp = "^[A-Za-z]{2}$", message = "Country code must be a 2-letter ISO country code, e.g. IN")
		String countryCode) {
}
