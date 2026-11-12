package com.nexhome.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OTP settings ({@code nexhome.otp.*} in application.properties).
 *
 * @param length number of digits in a generated OTP
 * @param expiryMinutes how long an OTP can be verified
 * @param maxAttempts wrong attempts after which the OTP is LOCKED
 * @param resendCooldownSeconds minimum time between two OTPs for the same mobile number and purpose
 * @param logPlaintext log generated OTPs; only for local development, where no SMS provider is configured
 * @param shareWithStaff keep customers' and partners' login OTPs (encrypted) for the console's Login OTPs screen,
 *        until an SMS provider sends them (temporary; {@code OTP_SHARE_WITH_STAFF}, default true)
 * @param fixedCodes testing only, set under the dev profile from {@code OTP_FIXED_CODES} (.env): numbers that always
 *        get the same code, {@code +918897182017:221916,+919876543210:123456}; empty everywhere else
 */
@ConfigurationProperties("nexhome.otp")
public record NexHomeOtpProperties(
		int length,
		int expiryMinutes,
		int maxAttempts,
		int resendCooldownSeconds,
		boolean logPlaintext,
		boolean shareWithStaff,
		String fixedCodes) {

	/** The fixed code of a number ("+91" + digits), or null. Malformed entries are ignored. */
	public String fixedCodeFor(String countryCode, String mobileNumber) {
		if (fixedCodes == null || fixedCodes.isBlank()) {
			return null;
		}
		String number = countryCode + mobileNumber;
		for (String entry : fixedCodes.split(",")) {
			String[] parts = entry.trim().split(":");
			if (parts.length == 2 && parts[0].trim().equals(number) && parts[1].trim().matches("[0-9]{4,8}")) {
				return parts[1].trim();
			}
		}
		return null;
	}
}
