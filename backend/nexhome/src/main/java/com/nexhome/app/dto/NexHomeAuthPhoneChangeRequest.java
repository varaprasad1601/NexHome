package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Sends a PHONE_CHANGE OTP to the signed-in user's new mobile number.
 */
public record NexHomeAuthPhoneChangeRequest(
		@NotBlank(message = "Country code is required")
		@Pattern(regexp = "^\\+[1-9][0-9]{0,3}$", message = "Country code must be a + followed by 1 to 4 digits")
		String countryCode,

		@NotBlank(message = "Mobile number is required")
		@Pattern(regexp = "^[0-9]{6,15}$", message = "Mobile number must be 6 to 15 digits")
		String mobileNumber) {
}
