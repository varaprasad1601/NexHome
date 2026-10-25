package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Verifies the pending OTP sent to a mobile number for a purpose.
 */
public record NexHomeAuthOtpVerifyRequest(
		@NotBlank(message = "Country code is required")
		@Pattern(regexp = "^\\+[1-9][0-9]{0,3}$", message = "Country code must be a + followed by 1 to 4 digits")
		String countryCode,

		@NotBlank(message = "Mobile number is required")
		@Pattern(regexp = "^[0-9]{6,15}$", message = "Mobile number must be 6 to 15 digits")
		String mobileNumber,

		@NotBlank(message = "Purpose is required")
		String purpose,

		@NotBlank(message = "OTP is required")
		@Pattern(regexp = "^[0-9]{4,8}$", message = "OTP must be 4 to 8 digits")
		String otp) {

	/** Never print the OTP, e.g. when the request is logged. */
	@Override
	public String toString() {
		return "NexHomeAuthOtpVerifyRequest[countryCode=" + countryCode + ", mobileNumber=" + mobileNumber
				+ ", purpose=" + purpose + ", otp=****]";
	}
}
