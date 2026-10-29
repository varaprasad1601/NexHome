package com.nexhome.app.dto;

import java.util.Map;

/** Every notification: in all, unread, by type, and delivery attempts by status. */
public record NexHomeNotificationStatsResponse(
		long total,
		long unread,
		Map<String, Long> byType,
		Map<String, Long> deliveriesByStatus) {
}
