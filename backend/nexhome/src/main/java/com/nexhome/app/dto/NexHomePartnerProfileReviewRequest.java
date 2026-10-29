package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Staff decision on a partner profile: the next PARTNER_ONBOARDING_STATUS (APPROVED, REJECTED or CHANGES_REQUESTED,
 * returning it to the partner for more information or documents). The reason is required for REJECTED and
 * CHANGES_REQUESTED.
 */
public record NexHomePartnerProfileReviewRequest(
		@NotNull(message = "Partner profile ID is required")
		Long partnerProfileId,

		@NotBlank(message = "Onboarding status is required")
		String onboardingStatus,

		@Size(max = 2000, message = "Rejection reason must be at most 2000 characters")
		String rejectionReason) {
}
