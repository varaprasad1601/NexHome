package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomePolicyDocumentResponse(
		Long policyDocumentId,

		String policyKey,

		String title,

		String audience,

		String version,

		String content,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant effectiveFrom,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant publishedAt,

		boolean isPublished,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
