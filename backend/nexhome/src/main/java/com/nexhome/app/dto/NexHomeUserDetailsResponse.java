package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeUserDetailsResponse(
		Long userDetailsId,
		Long userId,
		String firstName,
		String lastName,
		String fullName,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en", timezone = "Asia/Kolkata")
		Instant creationDate) {
}
