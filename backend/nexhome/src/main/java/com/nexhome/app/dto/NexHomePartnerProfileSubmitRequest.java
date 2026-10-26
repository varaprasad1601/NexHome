package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/** Submits a DRAFT or PROFILE_INCOMPLETE partner profile for review. */
public record NexHomePartnerProfileSubmitRequest(
		@NotNull(message = "Partner profile ID is required")
		Long partnerProfileId) {
}
