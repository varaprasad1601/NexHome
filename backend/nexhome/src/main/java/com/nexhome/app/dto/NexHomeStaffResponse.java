package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * One row of the admin console's staff list: the account, its highest staff role (SUPER_USER, then ADMIN, then
 * SUB_ADMIN) and the staff profile's name and public id (null while the account has no profile).
 */
public record NexHomeStaffResponse(
		Long userId,
		Long adminProfileId,
		String publicId,
		String firstName,
		String lastName,
		String email,
		String countryCode,
		String mobileNumber,
		String roleCode,
		String roleName,
		String accountStatus,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant lastLoginAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
