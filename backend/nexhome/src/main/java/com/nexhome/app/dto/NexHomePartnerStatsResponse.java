package com.nexhome.app.dto;

/**
 * Partner counts for the console's Manage Partners tiles.
 *
 * @param total current PARTNER holders
 * @param approved onboarding APPROVED
 * @param awaitingReview onboarding UNDER_REVIEW: waiting for staff
 * @param notSubmitted NOT_STARTED, DRAFT or CHANGES_REQUESTED (or no profile): waiting for the partner
 * @param online approved partners whose availability is ONLINE
 */
public record NexHomePartnerStatsResponse(
		long total,
		long approved,
		long awaitingReview,
		long notSubmitted,
		long online) {
}
