package com.nexhome.app.dto;

/**
 * A partner offering a service, and whether they are free at the time: approved, working then, not on leave and not
 * booked. {@code busyReason} says why not: OUTSIDE_WEEKLY_HOURS (they can still be booked then, see
 * {@code outsideWeeklyHours} on holds), TIME_OFF or BOOKED; null when free.
 */
public record NexHomeAvailablePartnerResponse(
		Long partnerProfileId,
		String publicId,
		Long userId,
		String firstName,
		String lastName,
		String city,
		String availabilityStatus,
		Short experienceYears,
		boolean isPrimaryService,
		boolean isFree,
		String busyReason) {
}
