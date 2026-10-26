package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomePolicyAcceptanceResponse(
		Long policyAcceptanceId,

		Long userId,

		Long policyDocumentId,

		String policyKey,

		String version,

		String title,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant acceptedAt,

		String acceptanceMethod,

		String ipAddress) {
}
