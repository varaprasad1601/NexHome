package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/** A ticket message or internal note; {@code fromStaff}: the sender is staff (not the requester). */
public record NexHomeSupportMessageResponse(
		Long messageId,
		Long senderUserId,
		String senderName,
		String senderRole,
		boolean fromStaff,
		String message,
		boolean isInternalNote,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
