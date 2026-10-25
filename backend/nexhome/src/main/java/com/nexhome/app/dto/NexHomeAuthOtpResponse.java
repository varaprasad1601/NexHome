package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * An OTP request's state; never contains the OTP.
 *
 * @param user the account after a successful verification; null otherwise
 * @param tokens a new login session after a successful LOGIN or REGISTRATION verification; null otherwise
 */
public record NexHomeAuthOtpResponse(
		Long otpRequestId,
		String countryCode,
		String mobileNumber,
		String purpose,
		String requestStatus,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant expiresAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant verifiedAt,
		NexHomeUserResponse user,
		NexHomeAuthTokenResponse tokens) {
}
