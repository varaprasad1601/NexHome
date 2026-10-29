package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Staff (NOTIFICATION_MANAGE write) send a notification. {@code audience}: USER (then {@code userId}), CUSTOMERS,
 * PARTNERS or STAFF (every active account currently holding those roles). {@code notificationType}:
 * NOTIFICATION_TYPE (default ANNOUNCEMENT).
 */
public record NexHomeNotificationSendRequest(
		@NotBlank(message = "Audience is required")
		String audience,
		Long userId,
		String notificationType,
		@NotBlank(message = "Title is required")
		@Size(max = 200, message = "Title must be at most 200 characters")
		String title,
		@NotBlank(message = "Message is required")
		@Size(max = 2000, message = "Message must be at most 2000 characters")
		String message) {
}
