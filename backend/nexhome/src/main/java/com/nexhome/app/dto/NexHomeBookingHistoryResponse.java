package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * One status change, with who made it (name and role, as the audit trails).
 */
public record NexHomeBookingHistoryResponse(
		Long historyId,
		String oldStatus,
		String newStatus,
		Long changedBy,
		String changedByName,
		String changedByRole,
		String reason,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant changedAt) {
}
