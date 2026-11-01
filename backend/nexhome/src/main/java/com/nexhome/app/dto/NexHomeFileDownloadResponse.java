package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A short-lived link to open or download a file: S3's presigned link, or with local storage the application's signed
 * {@code /api/files/content/...} address (relative to the API). It works without the access token until
 * {@code expiresAt}.
 */
public record NexHomeFileDownloadResponse(
		Long fileId,
		String url,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant expiresAt,
		String fileName,
		String contentType) {
}
