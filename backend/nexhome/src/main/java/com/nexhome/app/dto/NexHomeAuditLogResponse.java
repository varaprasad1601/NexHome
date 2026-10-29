package com.nexhome.app.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * One audit entry: who (name and highest role as audit entries show them; null actor: the application itself), what
 * and to which record, the values before and after, and the client's address.
 */
public record NexHomeAuditLogResponse(
		Long auditLogId,
		Long actorUserId,
		String actorName,
		String actorRole,
		String action,
		String entityType,
		String entityId,
		Map<String, Object> oldValues,
		Map<String, Object> newValues,
		String ipAddress,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
