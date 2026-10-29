package com.nexhome.app.dto;

import java.util.Map;

/**
 * Ticket counts: all, by status, open ones (not RESOLVED or CLOSED), open URGENT / HIGH ones and open ones nobody is
 * handling yet.
 */
public record NexHomeSupportStatsResponse(
		long total,
		Map<String, Long> byStatus,
		long open,
		long highPriorityOpen,
		long unassignedOpen) {
}
