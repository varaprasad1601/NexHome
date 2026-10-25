package com.nexhome.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self sign-up of a customer or partner: creates the account and sends a REGISTRATION OTP. Staff accounts are created
 * by an admin (POST /api/users/create).
 */
public record NexHomeAuthRegisterRequest(
		@NotBlank(message = "Country code is required")
		@Pattern(regexp = "^\\+[1-9][0-9]{0,3}$", message = "Country code must be a + followed by 1 to 4 digits")
		String countryCode,

		@NotBlank(message = "Mobile number is required")
		@Pattern(regexp = "^[0-9]{6,15}$", message = "Mobile number must be 6 to 15 digits")
		String mobileNumber,

		@Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "Email must be a valid email address")
		@Size(max = 255, message = "Email must be at most 255 characters")
		String email,

		@NotBlank(message = "Role is required")
		@Pattern(regexp = "^\\s*(CUSTOMER|PARTNER)\\s*$", message = "Role must be CUSTOMER or PARTNER")
		String roleCode) {
}
