package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeServiceCategoryResponse(
		Long serviceCategoryId,

		String publicId,

		String name,

		String slug,

		String description,

		String iconUrl,

		int displayOrder,

		boolean isActive,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate,

		/** Partners whose main service is this category; only in the staff list (includeInactive), else null. */
		Long partnerCount) {
}
