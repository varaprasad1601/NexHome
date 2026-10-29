package com.nexhome.app.dto;

import java.util.List;

/**
 * A booking with its line items, status history (oldest first), assignments (newest first), timer events, the
 * minutes worked so far and the partner's latest position. {@code categoryId} / {@code categoryName}: the service's
 * category (now; bookings keep no snapshot of it).
 */
public record NexHomeBookingDetailResponse(
		NexHomeBookingResponse booking,
		Long categoryId,
		String categoryName,
		List<NexHomeBookingItemResponse> items,
		List<NexHomeBookingHistoryResponse> history,
		List<NexHomeBookingAssignmentResponse> assignments,
		List<NexHomeBookingTimeLogResponse> timeLogs,
		long workedMinutes,
		NexHomePartnerLocationResponse latestLocation) {
}
