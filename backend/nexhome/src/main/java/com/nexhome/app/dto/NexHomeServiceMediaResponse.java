package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeServiceMediaResponse(
		Long serviceMediaId,

		Long serviceId,

		String mediaType,

		String storageKey,

		int displayOrder,

		boolean isActive,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
