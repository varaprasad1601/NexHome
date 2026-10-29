package com.nexhome.app.dto;

import java.util.Map;

/** The caller's notifications: how many in all and unread, and unread by type. */
public record NexHomeNotificationSummaryResponse(
		long total,
		long unread,
		Map<String, Long> unreadByType) {
}
