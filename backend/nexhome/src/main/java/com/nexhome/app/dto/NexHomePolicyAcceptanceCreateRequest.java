package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Records that the caller accepts the current version of a policy.
 */
public record NexHomePolicyAcceptanceCreateRequest(
		@NotNull(message = "Policy document ID is required")
		Long policyDocumentId,

		@NotBlank(message = "Acceptance method is required")
		String acceptanceMethod) {
}
