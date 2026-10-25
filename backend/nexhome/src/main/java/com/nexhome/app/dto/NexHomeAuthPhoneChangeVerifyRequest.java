package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Verifies the PHONE_CHANGE OTP sent to the new mobile number and moves the signed-in user's account to it.
 */
public record NexHomeAuthPhoneChangeVerifyRequest(
		@NotBlank(message = "Country code is required")
		@Pattern(regexp = "^\\+[1-9][0-9]{0,3}$", message = "Country code must be a + followed by 1 to 4 digits")
		String countryCode,

		@NotBlank(message = "Mobile number is required")
		@Pattern(regexp = "^[0-9]{6,15}$", message = "Mobile number must be 6 to 15 digits")
		String mobileNumber,

		@NotBlank(message = "OTP is required")
		@Pattern(regexp = "^[0-9]{4,8}$", message = "OTP must be 4 to 8 digits")
		String otp) {

	/** Never print the OTP, e.g. when the request is logged. */
	@Override
	public String toString() {
		return "NexHomeAuthPhoneChangeVerifyRequest[countryCode=" + countryCode + ", mobileNumber=" + mobileNumber
				+ ", otp=****]";
	}
}
