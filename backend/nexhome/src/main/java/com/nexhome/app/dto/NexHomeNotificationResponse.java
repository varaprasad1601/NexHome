package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * An in-app notification. {@code referenceType} / {@code referenceId}: what it is about (BOOKING, SUPPORT_TICKET,
 * REVIEW, PARTNER_PROFILE and that record's id), for the client to open. {@code userName} / {@code userRole} only
 * in the console's list of every notification.
 */
public record NexHomeNotificationResponse(
		Long notificationId,
		Long userId,
		String userName,
		String userRole,
		String notificationType,
		String title,
		String message,
		String referenceType,
		Long referenceId,
		boolean isRead,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant readAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
