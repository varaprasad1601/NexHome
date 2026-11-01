package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A file on a ticket. It opens through {@code GET /api/files/download-url/{fileId}} (null for attachments recorded
 * by key before file storage existed).
 */
public record NexHomeSupportAttachmentResponse(
		Long attachmentId,
		Long messageId,
		Long fileId,
		String fileName,
		String fileType,
		Long fileSize,
		Long createdBy,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
