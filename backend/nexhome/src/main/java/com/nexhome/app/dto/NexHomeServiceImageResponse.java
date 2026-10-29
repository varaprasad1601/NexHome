package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A service image without its bytes.
 *
 * @param imageUrl the path that returns the image ({@code GET /api/catalog/services/images/{serviceImageId}}, public)
 */
public record NexHomeServiceImageResponse(
		Long serviceImageId,
		Long serviceId,
		String fileName,
		String contentType,
		int fileSize,
		int displayOrder,
		String imageUrl,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
