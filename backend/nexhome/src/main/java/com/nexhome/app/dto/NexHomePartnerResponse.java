package com.nexhome.app.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * One row of the admin console's partner list: the account with its roles and, once the partner has started their
 * onboarding profile, its id, public id, name, city, main service category and the onboarding, verification and
 * availability statuses (all null before that).
 */
public record NexHomePartnerResponse(
		Long userId,
		Long partnerProfileId,
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
		String city,
		Long mainServiceCategoryId,
		String mainServiceCategoryName,
		String onboardingStatus,
		String approvalStatus,
		String availabilityStatus,
		List<NexHomeUserRoleResponse> roles,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
