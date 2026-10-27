package com.nexhome.app.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * One row of the admin console's customer list: the account with its roles (as {@link NexHomeUserResponse}) and the
 * customer profile's id, public id and name (null while the customer has not created a profile).
 */
public record NexHomeCustomerResponse(
		Long userId,
		Long customerProfileId,
		String publicId,
		String firstName,
		String lastName,
		String countryCode,
		String mobileNumber,
		String email,
		String profilePhotoUrl,
		String accountStatus,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant accountVerifiedDate,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant lastLoginAt,
		List<NexHomeUserRoleResponse> roles,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
