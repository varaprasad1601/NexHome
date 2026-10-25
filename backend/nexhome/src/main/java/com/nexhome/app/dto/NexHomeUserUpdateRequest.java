package com.nexhome.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates a user's email and profile photo; a null (or missing) field keeps its current value and an empty one clears
 * it. The mobile number only changes through a verified PHONE_CHANGE OTP, the account status has its own endpoint
 * (PUT /api/users/status/update) and roles have theirs (/api/users/roles/...). They are only declared here so that a
 * request containing them is rejected with a clear message instead of being silently ignored.
 */
public record NexHomeUserUpdateRequest(
		@NotNull(message = "User ID is required")
		Long userId,

		@Null(message = "Country code can only be changed with a phone change OTP")
		String countryCode,

		@Null(message = "Mobile number can only be changed with a phone change OTP")
		String mobileNumber,

		@Email(regexp = "^$|^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "Email must be a valid email address")
		@Size(max = 255, message = "Email must be at most 255 characters")
		String email,

		@Pattern(regexp = "^$|^https?://\\S+$", message = "Profile photo URL must be an http or https URL")
		String profilePhotoUrl,

		@Null(message = "Account status cannot be updated here")
		String accountStatus,

		@Null(message = "Role cannot be updated here")
		Short roleId) {
}
