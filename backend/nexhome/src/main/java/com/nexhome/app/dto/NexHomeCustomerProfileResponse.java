package com.nexhome.app.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeCustomerProfileResponse(
		Long customerProfileId,
		String publicId,
		Long userId,
		String firstName,
		String lastName,
		LocalDate dateOfBirth,
		String preferredLanguage,
		boolean marketingConsent,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
