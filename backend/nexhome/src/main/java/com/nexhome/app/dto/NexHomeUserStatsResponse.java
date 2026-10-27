package com.nexhome.app.dto;

import java.util.List;

/**
 * Customer and partner counts for the admin dashboard. Counts are of users who currently hold the role (active
 * assignment of an active role), by the month they were given it (IST).
 */
public record NexHomeUserStatsResponse(
		RoleStats customers,
		RoleStats partners) {

	/**
	 * @param total current holders of the role
	 * @param addedThisMonth of those, given the role this calendar month
	 * @param months the last 10 months, oldest first, ending with this month
	 */
	public record RoleStats(
			String roleCode,
			long total,
			long addedThisMonth,
			List<MonthStats> months) {
	}

	/**
	 * @param month {@code YYYY-MM}
	 * @param added given the role in that month
	 * @param total holders at the end of that month (for this month: now)
	 */
	public record MonthStats(
			String month,
			long added,
			long total) {
	}
}
