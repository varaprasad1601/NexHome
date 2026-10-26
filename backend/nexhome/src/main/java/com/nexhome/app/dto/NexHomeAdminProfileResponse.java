package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeAdminProfileResponse(
		Long adminProfileId,
		String publicId,
		Long userId,
		String firstName,
		String lastName,
		String employeeCode,
		String department,
		String jobTitle,
		String addressLine1,
		String addressLine2,
		String landmark,
		String city,
		String state,
		String postalCode,
		String countryCode,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
