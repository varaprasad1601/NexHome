package com.nexhome.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creates an ACTIVE account with one role. The mobile number is verified later, by the first REGISTRATION or LOGIN
 * OTP.
 */
public record NexHomeUserCreateRequest(
		@NotBlank(message = "Country code is required")
		@Pattern(regexp = "^\\+[1-9][0-9]{0,3}$", message = "Country code must be a + followed by 1 to 4 digits")
		String countryCode,

		@NotBlank(message = "Mobile number is required")
		@Pattern(regexp = "^[0-9]{6,15}$", message = "Mobile number must be 6 to 15 digits")
		String mobileNumber,

		// Optional; when given, it needs a domain with a dot (plain @Email accepts "name@host")
		@Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "Email must be a valid email address")
		@Size(max = 255, message = "Email must be at most 255 characters")
		String email,

		@Pattern(regexp = "^https?://\\S+$", message = "Profile photo URL must be an http or https URL")
		String profilePhotoUrl,

		@NotNull(message = "Role is required")
		Short roleId) {
}
