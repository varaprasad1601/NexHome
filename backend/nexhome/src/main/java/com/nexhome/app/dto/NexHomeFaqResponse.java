package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeFaqResponse(
		Long faqId,

		String question,

		String answer,

		Long categoryId,

		Long serviceId,

		String audience,

		int displayOrder,

		boolean isPublished,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
