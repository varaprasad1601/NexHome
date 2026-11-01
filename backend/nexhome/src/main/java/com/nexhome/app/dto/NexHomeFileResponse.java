package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * An uploaded file. {@code url} is the public address of a PUBLIC file (relative to the API, e.g.
 * {@code /api/files/public/<uuid>}); a PRIVATE file has none: ask for a short-lived link with
 * {@code GET /api/files/download-url/{fileId}}. The storage key is never returned.
 */
public record NexHomeFileResponse(
		Long fileId,
		String publicId,
		String purpose,
		String visibility,
		String originalName,
		String contentType,
		long fileSize,
		String fileStatus,
		String referenceType,
		Long referenceId,
		String url,
		Long createdBy,
		String createdByName,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
