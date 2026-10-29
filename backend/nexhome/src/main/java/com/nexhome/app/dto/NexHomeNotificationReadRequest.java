package com.nexhome.app.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Marks the caller's notifications read (others' ids are ignored). */
public record NexHomeNotificationReadRequest(
		@NotEmpty(message = "Notification IDs are required")
		@Size(max = 100, message = "At most 100 notifications can be marked at once")
		List<Long> notificationIds) {
}
