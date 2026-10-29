package com.nexhome.app.dto;

import java.util.List;
import java.util.Map;

/**
 * Booking counts for the console: all, by status, those scheduled today (IST), and those made per month.
 *
 * @param addedThisMonth bookings made this calendar month (IST)
 * @param months the last 10 months, oldest first, ending with this month
 */
public record NexHomeBookingStatsResponse(
		long total,
		Map<String, Long> byStatus,
		long scheduledToday,
		long addedThisMonth,
		List<MonthStats> months) {

	/**
	 * @param month {@code YYYY-MM}
	 * @param added bookings made in that month
	 * @param total bookings at the end of that month (for this month: now)
	 */
	public record MonthStats(
			String month,
			long added,
			long total) {
	}
}
