package com.nexhome.app.dto;

/**
 * Accounts of a console list (the staff the caller may see, or customers): all of them, and by account status
 * (inactive = DEACTIVATED).
 */
public record NexHomeStaffStatsResponse(
		long total,
		long active,
		long inactive,
		long suspended,
		long blocked) {
}
