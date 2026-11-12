package com.nexhome.app.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A customer's or partner's login / sign-up OTP for staff to share (V61, temporary until OTPs are sent by SMS).
 * {@code otp} is filled only while the OTP can still be used (PENDING and not expired); {@code requestStatus} shows
 * EXPIRED for a pending one past its expiry. {@code roles}: the account's active role codes; {@code requestedFrom}
 * (V65): the app that asked, CUSTOMER, PARTNER or STAFF (the admin console), null for older OTPs.
 */
public record NexHomeLoginOtpResponse(
		Long otpRequestId,
		Long userId,
		List<String> roles,
		String requestedFrom,
		String countryCode,
		String mobileNumber,
		String purpose,
		String requestStatus,
		String otp,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant expiresAt,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant verifiedAt) {

	@Override
	public String toString() {
		return "NexHomeLoginOtpResponse[otpRequestId=" + otpRequestId + ", userId=" + userId + ", purpose=" + purpose
				+ ", requestStatus=" + requestStatus + ", otp=" + (otp == null ? null : "******") + "]";
	}
}
