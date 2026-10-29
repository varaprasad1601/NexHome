package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/** Submits a partner profile not submitted yet (NOT_STARTED, DRAFT) or with CHANGES_REQUESTED for review. */
public record NexHomePartnerProfileSubmitRequest(
		@NotNull(message = "Partner profile ID is required")
		Long partnerProfileId) {
}
